package nars.model.impl;

import nars.Memory;
import nars.NAR;
import nars.bag.Bag;
import nars.bag.impl.CacheBag;
import nars.bag.impl.CurveBag;
import nars.bag.impl.GuavaCacheBag;
import nars.bag.impl.experimental.ChainBag;
import nars.budget.Budget;
import nars.budget.BudgetFunctions;
import nars.model.ControlCycle;
import nars.model.cycle.ConceptActivator;
import nars.nal.Sentence;
import nars.nal.Task;
import nars.nal.TaskComparator;
import nars.nal.concept.Concept;
import nars.nal.concept.DefaultConcept;
import nars.nal.process.ConceptProcess;
import nars.nal.process.TaskProcess;
import nars.nal.term.Term;
import nars.nal.tlink.TaskLink;
import nars.nal.tlink.TermLink;
import nars.util.data.id.Identifier;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;

/** processes every concept fairly, according to priority, in each cycle
 *
 * TODO eliminate ConcurrentSkipListSet like is implemented in DefaultCore
 * */
public class Solid extends Default implements ControlCycle {


    private final int maxConcepts, maxSubConcepts;
    private int maxTasksPerCycle = -1; //if ==-1, no limit
    private final int minTaskLink;
    private final int maxTaskLink;
    private final int minTermLink;
    private final int maxTermLink;
    private final int inputsPerCycle;
    private Memory memory;

    public final CacheBag<Term, Concept> subcon;

    public final Bag<Term, Concept> concepts;

    final ConceptActivator activator = new ConceptActivator() {
        @Override
        public Memory getMemory() {
            return memory;
        }

        @Override
        public CacheBag<Term, Concept> getSubConcepts() {
            return subcon;
        }

    };

    final SortedSet<Task> tasks = new ConcurrentSkipListSet<>(new TaskComparator(TaskComparator.Merging.Or));
    //final SortedSet<Task> tasks = new TreeSet(new TaskComparator(TaskComparator.Duplication.Or));
        /*final SortedSet<Task> tasks = new FastSortedSet(new WrapperComparatorImpl(new TaskComparator(TaskComparator.Duplication.Or))).atomic();*/

    int tasksAddedThisCycle = 0;


    public Solid(int inputsPerCycle, int maxConcepts, int minTaskLink, int maxTaskLink, int minTermLink, int maxTermLink) {
        super();
        this.inputsPerCycle = inputsPerCycle;
        this.maxConcepts = maxConcepts;
        this.maxSubConcepts = maxConcepts * 4;

        //this.maxTasks = maxConcepts * maxTaskLink * maxTermLink * 2;
        this.maxTasksPerCycle = -1;

        this.minTaskLink = minTaskLink;
        this.maxTaskLink = maxTaskLink;
        this.minTermLink = minTermLink;
        this.maxTermLink = maxTermLink;
        duration.set(1);
        noveltyHorizon.set(0.9f);
        termLinkForgetDurations.set(1);
        taskLinkForgetDurations.set(1);
        conceptForgetDurations.set(1);

        setTermLinkBagSize(16);
        setTaskLinkBagSize(16);

        subcon = new GuavaCacheBag(maxSubConcepts);

        concepts = new CurveBag(rng, maxConcepts, true);
        //concepts = new ChainBag(rng, maxConcepts);
        //concepts = new BubbleBag(rng, maxConcepts);
        //concepts = new HeapBag(rng, maxConcepts);
        //concepts = new LevelBag(100, maxConcepts);
    }

    @Override
    public void init(NAR n) {
        super.init(n);
        this.memory = n.memory;

    }






        @Override
        public double conceptMass() {
            return concepts.mass();
        }


        @Override
        public Iterator<Concept> iterator() {
            return concepts.iterator();
        }

        @Override
        public void addTask(Task t) {
            tasks.add(t);
            tasksAddedThisCycle++;
        }

        @Override
        public int size() {
            return concepts.size();
        }

        protected int num(float p, int min, int max) {
            return Math.round((p * (max - min)) + min);
        }



        protected void processNewTasks() {
            int t = 0;
            final int mt = maxTasksPerCycle;
            int nt = tasks.size();

            long now = getMemory().time();

            float maxPriority = -1, currentPriority = -1;
            float maxQuality = Float.MIN_VALUE, minQuality = Float.MAX_VALUE;
            for (Task task : tasks) {

                currentPriority = task.getPriority();
                if (maxPriority == -1) maxPriority = currentPriority; //first one is highest

                float currentQuality = task.getQuality();
                if (currentQuality < minQuality) minQuality = currentQuality;
                else if (currentQuality > maxQuality) maxQuality = currentQuality;

                if (TaskProcess.run(getMemory(), task)!=null) {
                    t++;
                    if (mt!=-1 && t >= mt) break;
                }
            }

            /*
            System.out.print(tasksAddedThisCycle + " added, " + nt + " unique  ");
            System.out.print("pri=[" + currentPriority + " .. " + maxPriority + "]  ");
            System.out.print("qua=[" + minQuality + " .. " + maxQuality + "]  ");
            System.out.println();
            */

            tasks.clear();
            tasksAddedThisCycle = 0;
        }

        @Override
        public void cycle() {
            //System.out.println("\ncycle " + memory.time() + " : " + concepts.size() + " concepts");

            getMemory().perceiveNext(inputsPerCycle);

            processNewTasks();

            //2. fire all concepts
            for (Concept c : concepts) {

                if (c == null) break;

                int conceptTaskLinks = c.getTaskLinks().size();
                if (conceptTaskLinks == 0) continue;

                float p = c.getPriority();
                int fires = num(p, minTaskLink, maxTaskLink);
                if (fires < 1) continue;
                int termFires = num(p, minTermLink, maxTermLink);
                if (termFires < 1) continue;

                for (int i = 0; i < fires; i++) {
                    TaskLink tl = c.getTaskLinks().forgetNext(taskLinkForgetDurations, getMemory());
                    if (tl==null) break;
                    new ConceptProcess(c, tl, termFires).run();
                }

            }

            memory.runNextTasks();
        }

        @Override
        public void reset(boolean delete) {
            tasks.clear();

            if (delete)
                concepts.delete();
            else
                concepts.clear();

            subcon.clear();
        }

        @Override
        public Concept concept(Term term) {
            return concepts.get(term);
        }

        @Override
        public Concept conceptualize(Budget budget, Term term, boolean createIfMissing) {
            //synchronized(activator) {
                activator.set(term, budget, true, getMemory().time());
                return concepts.update(activator);
            //}
        }

        @Override
        @Deprecated public void activate(Concept c, Budget b, BudgetFunctions.Activating mode) {

        }

        @Override
        public Concept nextConcept() {
            return concepts.peekNext();
        }

        @Override
        public void init(Memory m) {
            subcon.setMemory(m);
        }

        @Override
        public boolean conceptRemoved(Concept c) {
            subcon.put(c);
            return false;
        }

        @Override
        public Memory getMemory() {
            return memory;
        }


    @Override
    public ControlCycle newControlCycle() {
        return this;
    }

    public void setMaxTasksPerCycle(int maxTasksPerCycle) {
        this.maxTasksPerCycle = maxTasksPerCycle;
    }

    @Override
    public Concept newConcept(Term t, Budget b, Memory m) {
        Bag<Sentence, TaskLink> taskLinks = new ChainBag(rng, getConceptTaskLinks());
        Bag<Identifier, TermLink> termLinks = new ChainBag(rng, getConceptTermLinks());

        return new DefaultConcept(t, b, taskLinks, termLinks, m);
        //return super.newConcept(b, t, m);
    }


    /*
    static final Comparator<Item> budgetComparator = new Comparator<Item>() {
        //almost...
        //> Math.pow(2.0,32.0) * 0.000000000001
        //0.004294967296

        //one further is below 0.001 resolution
        //> Math.pow(2.0,32.0) * 0.0000000000001
        //0.0004294967296

        @Override
        public int compare(final Item o1, final Item o2) {
            if (o1.equals(o2)) return 0; //is this necessary?
            float p1 = o1.getPriority();
            float p2 = o2.getPriority();
            if (p1 == p2) {
                float d1 = o1.getDurability();
                float d2 = o2.getDurability();
                if (d1 == d2) {
                    float q1 = o1.getQuality();
                    float q2 = o2.getQuality();
                    if (q1 == q2) {
                        return Integer.compare(o1.hashCode(), o2.hashCode());
                    }
                    else {
                        return q1 < q2 ? -1 : 1;
                    }
                }
                else {
                    return d1 < d2 ? -1 : 1;
                }
            }
            else {
                return p1 < p2 ? -1 : 1;
            }
        }
    };
    */

}
