package ca.nengo.test;

import ca.nengo.ui.model.plot.Cursor;
import ca.nengo.model.StructuralException;
import ca.nengo.model.impl.DefaultNetwork;
import ca.nengo.model.impl.NetworkImpl;
import ca.nengo.ui.NengrowPanel;

/** example in nengrow panel */
public class TestCursor extends NengrowPanel {


    public static NetworkImpl newDemo() throws StructuralException {
        NetworkImpl network = new DefaultNetwork<>();

        Cursor c = new Cursor("cursor, baby!", 16, 64, network);

        network.addNode(c);

        //network.addStepListener(c.subCycle);

        return network;
    }



    public static void main(String[] args) throws ContainerException, StructuralException {
        new TestCursor().newWindow(900, 800);
    }

    public TestCursor() throws StructuralException, ContainerException {
        super(newDemo());
    }

}
