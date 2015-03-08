/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package nars.gui.output;

import automenta.vivisect.Video;
import automenta.vivisect.swing.NPanel;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import nars.core.Events;
import nars.core.Events.FrameEnd;
import nars.core.NAR;
import nars.event.AbstractReaction;
import nars.logic.entity.BudgetValue.Budgetable;
import nars.logic.entity.Concept;
import nars.logic.entity.Sentence;
import nars.logic.entity.TermLink;
import nars.logic.entity.TruthValue.Truthable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static java.awt.BorderLayout.*;

/**
 * Manages a set of ConceptPanels by receiving events and dispatching update commands
 * TODO use a minimum framerate for updating
 */
public class ConceptPanelBuilder extends AbstractReaction {

    private final NAR nar;
    private final Multimap<Concept, ConceptPanel> concept = HashMultimap.create();

    public ConceptPanelBuilder(NAR n, Concept... c) {
        super(n, Events.FrameEnd.class,
                Events.ConceptBeliefAdd.class,
                Events.ConceptBeliefRemove.class,
                Events.ConceptQuestionAdd.class,
                Events.ConceptQuestionRemove.class,
                Events.ConceptGoalAdd.class,
                Events.ConceptGoalRemove.class);

        this.nar = n;

    }

    public ConceptPanel newPanel(Concept c, boolean label, int chartSize) {
        return new ConceptPanel(c, label, chartSize) {
            @Override
            protected void onShowing(boolean showing) {
                if (showing) {
                    concept.put(c, this);
                }
                else {
                    concept.remove(c, this);
                }
            }
        }.update(nar.time());
    }

    @Override
    public void event(Class event, Object[] args) {

        if (event == FrameEnd.class) {
            //SwingUtilities.invokeLater(this);
            updateAll();
        }
    }
    
    public void updateAll() {
        //TODO only update the necessary concepts
        long t = nar.time();
        for (final ConceptPanel cp : concept.values())
            if (cp.isShowing() && cp.isVisible())
                cp.update(t);
    }

    public void off() {
        super.off();
        concept.clear();
    }

    public static class ConceptPanel extends NPanel {

        private final Concept concept;
        private final TruthChart beliefChart;
        private final TruthChart desireChart;
        private final PriorityColumn questionChart;
        private final TermLinkChart termLinkChart;
        private JTextArea title;
        private JLabel subtitle;

        int chartWidth = 64;
        int chartHeight = 64;
        final float titleSize = 24f;
        //final float subfontSize = 16f;
        private BeliefTimeline beliefTime;
       // private final PCanvas syntaxPanel;


        public ConceptPanel(Concept c, boolean label, int chartSize) {
            super(new BorderLayout());
            this.concept = c;

            this.chartWidth = this.chartHeight = chartSize;
            setOpaque(false);


            JPanel details = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
            details.setOpaque(false);

            details.add(this.beliefTime = new BeliefTimeline(chartWidth/2, chartHeight));
            details.add(this.beliefChart = new TruthChart(chartWidth, chartHeight));

            details.add(this.questionChart = new PriorityColumn((int)Math.ceil(Math.sqrt(chartWidth)), chartHeight));

            details.add(this.desireChart = new TruthChart(chartWidth, chartHeight));
            //details.add(this.questChart = new PriorityColumn((int)Math.ceil(Math.sqrt(chartWidth)), chartHeight)));
            details.add(this.termLinkChart = new TermLinkChart(c));

            JPanel titlePanel = new JPanel(new BorderLayout());
            titlePanel.setOpaque(false);

            if (label) {
                titlePanel.add(this.title = new JTextArea(concept.term.toString()), CENTER);
                title.setWrapStyleWord(true);
                title.setLineWrap(true);
                title.setEditable(false);
                title.setOpaque(false);
                title.setFont(Video.monofont.deriveFont(titleSize));
                titlePanel.add(this.subtitle = new JLabel(), SOUTH);

                add(titlePanel, NORTH);
            }
            

            add(details, CENTER);

           /* TermSyntaxVis tt = new TermSyntaxVis(c.term);
            syntaxPanel = new PCanvas(tt);
            syntaxPanel.setZoom(10f);
            
            syntaxPanel.noLoop();
            syntaxPanel.redraw();

            
                        

            add(syntaxPanel);*/
            //setComponentZOrder(overlay, 1);
            //syntaxPanel.setBounds(0,0,400,400);
            
            
        }

        public ConceptPanel update(long time) {

            String st = "";

            beliefChart.setVisible(true);
            if (!concept.beliefs.isEmpty()) {
                List<Sentence> bb = concept.beliefs;
                beliefChart.update(time, bb);
                st += (bb.get(0).truth.toString()) + ' ';

                beliefTime.setVisible(
                        beliefTime.update(time, bb));
            }

            if (subtitle!=null)
                subtitle.setText(st);

            /*
            else {
                subtitle.setText("");
                if (!concept.questions.isEmpty())
                    subtitle.setText("?(question)");
                beliefTime.setVisible(false);
            }*/

            if (!concept.questions.isEmpty()) {
                questionChart.setVisible(true);
                questionChart.update(concept.questions);
            }
            else {
                questionChart.setVisible(false);
            }
            
            if (!concept.goals.isEmpty()) {
                desireChart.setVisible(true);
                if (subtitle!=null) {
                    String s = subtitle.getText();
                    subtitle.setText(s + (s.equals("") ? "" : " ") + "desire: " + concept.goals.get(0).truth.toString());
                }
                desireChart.update(time, concept.goals);
            }
            else {
                desireChart.setVisible(false);
            }

            if (termLinkChart!=null) {
                termLinkChart.update(time);
            }

            validate();

            return this;
        }

        @Override
        protected void onShowing(boolean showing) {
            validate();
        }


    }

    public static class ImagePanel extends JPanel {

        public BufferedImage image;
        final int w, h;

        public ImagePanel(int width, int height) {
            super();

            this.w = width;
            this.h = height;
            setSize(width, height);
            Dimension minimumSize = new Dimension(width, height);
            setMinimumSize(minimumSize);
            setPreferredSize(minimumSize);
        }

        public Graphics2D g() {
            if (image == null) {
                image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }
            if (image != null) {
                return image.createGraphics();
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, null);
        }

    }
    public static class PriorityColumn extends ImagePanel {

        public PriorityColumn(int width, int height) {
            super(width, height);
            update(Collections.EMPTY_LIST);
        }

        public void update(Iterable<? extends Budgetable> i) {
            Graphics g = g();
            if (g == null) return;
            
            g.setColor(new Color(0.1f, 0.1f, 0.1f));
            g.fillRect(0, 0, getWidth(), getHeight());
            for (Budgetable s : i) {
                float pri = s.getBudget().getPriority();
                float dur = s.getBudget().getDurability();

                float ii = 0.1f + pri * 0.9f;
                g.setColor(new Color(ii, ii, ii, 0.5f + 0.5f * dur));

                int h = 8;
                int y = (int)((1f - pri) * (getHeight() - h));
                
                g.fillRect(0, y-h/2, getWidth(), h);

            }
            g.dispose();            
        }
    }
    
    /** normalized to entire history of non-eternal beliefs;
     * displayed horizontally
     */
    public static class BeliefTimeline extends ImagePanel {

        float minTime, maxTime;
        private float timeFactor;
        
        public BeliefTimeline(int width, int height) {
            super(width, height);
        }

        public int getT(long when) {
            return Math.round((when - minTime) / timeFactor);
        }
        
        public boolean update(long time, Collection<Sentence> i) {
            
            minTime = maxTime = time;
            for (Sentence s : i) {
                if (s.isEternal()) continue;
                long when = s.getOccurrenceTime();
                                
                if (minTime > when)
                    minTime = when;                
                if (maxTime < when)
                    maxTime = when;
            }
            
            if (minTime == maxTime) {
                //no time-distinct beliefs
                return false;
            }

            Graphics g = g();
            if (g == null) return false;
            
            int thick = 4;                
            timeFactor = (maxTime - minTime) / ((float)w-thick);
            
            g.setColor(new Color(0.1f, 0.1f, 0.1f));
            g.fillRect(0, 0, getWidth(), getHeight());
            for (Sentence s : i) {
                if (s.isEternal()) continue;
                long when = s.getOccurrenceTime();
                
                int yy = getT(when);
                
                
                float freq = s.getTruth().getFrequency();
                float conf = s.getTruth().getConfidence();

                int xx = (int)((1.0f - freq) * (this.w - thick));
                        
                
                g.setColor(getColor(freq, conf, 1.0f));
                
                g.fillRect(xx, yy, thick, thick);

            }
            
            // "now" axis            
            g.setColor(Color.GRAY);
            int tt = getT(time);
            g.fillRect(0, tt-1, getWidth(), 3);
            
            g.dispose();        
            return true;
        }
    }
    
    public static Color getColor(float freq, float conf, float factor) {
        float ii = 0.25f + (factor * conf) * 0.75f;
        float green = freq > 0.5f ? (freq/2f) : 0f;
        float red = freq <= 0.5f ? ((1.0f-freq)/2f) : 0;
        return new Color(red, green, 1.0f, ii);
    }
    
    public static class TruthChart extends ImagePanel {

        public TruthChart(int width, int height) {
            super(width, height);
        }

        public void update(long now, Iterable<? extends Truthable> i) {
            Graphics g = g();
            if (g == null) return;
            
            g.setColor(new Color(0.1f, 0.1f, 0.1f));
            g.fillRect(0, 0, getWidth(), getHeight());
            for (Truthable s : i) {
                float freq = s.getTruth().getFrequency();
                float conf = s.getTruth().getConfidence();

                float factor = 1.0f;
                if (s instanceof Sentence) {
                    Sentence ss = (Sentence)s;
                    if (!ss.isEternal()) {
                        //float factor = TruthFunctions.temporalProjection(now, ss.getOccurenceTime(), now);
                        factor = 1.0f / (1f + Math.abs(ss.getOccurrenceTime() - now)  );
                    }
                }
                g.setColor(getColor(freq, conf, factor));

                int w = 6;
                int h = 6;
                float dw = getWidth() - w;
                float dh = getHeight() - h;
                g.fillRect((int) (freq * dw), (int) ((1.0 - conf) * dh), w, h);

            }
            g.dispose();

        }
    }

    public static class TermLinkChart extends ImagePanel {

        private final Concept concept;
        TreeMap<String,Float> priority = new TreeMap();

        public TermLinkChart(Concept c) {
            super(400,200);

            this.concept = c;
        }

        synchronized boolean updateData() {
            priority.clear();

            boolean changed = false;
            for (TermLink t : concept.termLinks) {
                String n = t.name().toString();
                float v = t.getPriority();
                float existing = priority.getOrDefault(n, -1f);
                if (existing==-1 || existing != v) {
                    priority.put(n, v);
                    changed = true;
                }
            }

            return (changed);
        }

        public void update(long now) {
            if (!updateData())
                return;

            repaint();
        }

        @Override
        public void paint(Graphics g1) {
            super.paint(g1);

            if (priority.isEmpty()) return;

            Graphics2D g = (Graphics2D)g1;

            g.setColor(new Color(0.1f, 0.1f, 0.1f));
            g.fillRect(0, 0, getWidth(), getHeight());

            int height = getHeight();
            float dx = getWidth() / (1 + priority.size());
            float x = dx/2;
            g.setFont(Video.monofont.deriveFont(8f));
            for (Map.Entry<String,Float> e : priority.entrySet()) {

                String s = e.getKey();
                //int textLength = g.getFontMetrics().stringWidth(s);
                float y = e.getValue() * (height*0.85f) + (height*0.1f);

                g.setColor(Color.getHSBColor(e.getValue()*0.5f + 0.25f, 0.75f, 0.8f ));
                //g.translate((int) x, (int) y /*+ textLength*/);

                //g.rotate(-Math.PI/2);
                g.drawString(s, (int)x, (int)y);
                //g.rotate(Math.PI/2);

                x += dx;
            }
            g.dispose();
        }

    }
}
