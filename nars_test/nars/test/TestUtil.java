/*
 * Copyright (C) 2014 me
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nars.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import nars.core.NAR;
import nars.entity.Sentence;
import nars.io.TextOutput;
import nars.io.TextPerception;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author me
 */
public class TestUtil {
    private final int performanceIterations = 4;

    ScriptEngineManager factory = new ScriptEngineManager();
    ScriptEngine js = factory.getEngineByName("JavaScript");
      

    protected Map<String, String> exCache = new HashMap(); //path -> script data
    private final boolean testPerformance;
    
    public TestUtil(boolean testPerformance) {
        this.testPerformance = testPerformance;        
    }
    
    
    public String getExample(String path)  {
        try {
            String existing = exCache.get(path);
            if (existing!=null)
                return existing;
            
            StringBuffer  sb  = new StringBuffer();
            String line;
            File fp = new File(path);
            BufferedReader br = new BufferedReader(new FileReader(fp));
            while ((line = br.readLine())!=null) {
                sb.append(line + "\n");
            }
            existing = sb.toString();
            exCache.put(path, existing);
            return existing;
        } catch (Exception ex) {
            assertTrue(path + ": " + ex.toString()  + ": ", false);
        }
        return "";
    }
        
    public boolean match(String x, String y) {
        if (x.equals(y))
            return true;
        return false;
    }
    
    
    final LinkedList<String> out = new LinkedList();

    public boolean outputContains(String s) {        
        for (String o : out) {
            if (o.indexOf(s)!=-1)
                return true;
        }
        return false;
    }
    

    public Sentence parseOutput(String o) {
        //getTruthString doesnt work yet because it gets confused when Stamp is at the end of the string. either remove that first or make getTruthString aware of that
        return TextPerception.parseOutput(o);
    }
    
    protected void testNAL(final String path) {
        final NAR n = new NAR();        
        
        
        final LinkedList<String> expressions = new LinkedList();
        out.clear();

        
        //new TextOutput(n, new PrintWriter(System.out));
        new TextOutput(n) {
            @Override
            public void output(Class c, Object line) {                
                if (c == ERR.class) {   
                    assertTrue(path + " ERR: " + line, false);
                }
                
                String s = line.toString();
                s = s.trim();

                if (c == ECHO.class) {
                    if (s.startsWith("\"\'")) {      
                        //remove begining "' and trailing "
                        String expression = s.substring(2, s.length()-1);
                        expressions.add(expression);
                        return;
                    }                        
                }

                if (c == OUT.class) {
                    out.add(s);
                }
            }            
        };         
        
        n.addInput(getExample(path));

        n.finish(1);


        js.put("test", this);
        js.put("out", out);

        if (expressions.isEmpty()) {
            assertTrue(path + " contains no expressions to evaluate",  false);
        }
        
        for (String e : expressions) {
            try {
                Object result = js.eval(e);
                if (result instanceof Boolean) {
                    boolean r = (Boolean)result;
                    assertTrue(path + ": " + e + " FAIL @ " + + n.getTime() + ", output lines=" + out.size(), r);
                }
            }
            catch (Exception x) {
                assertTrue(path + ": " + x.toString()  + ": ", false);                
            }            
        }
        
        if (testPerformance) {
            perfNAL(path, 0, performanceIterations, 1);            
        }
    }
    
    
    protected void perfNAL(final String path, final int extraCycles, int repeats, int warmups) {
        
        
        new Performance(path, repeats, warmups) {
            private NAR n;

            @Override
            public void init() {
                n = new NAR();
            }

            @Override
            public void run(boolean warmup) {
                n.reset();
                n.addInput(getExample(path));
                n.finish(extraCycles);
            }


        }.print();
        
           
    }
    
}
