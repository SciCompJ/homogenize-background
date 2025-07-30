/**
 * 
 */
package my.plugin;

import imago.gui.FramePlugin;
import imago.gui.ImagoFrame;

/**
 * Example plugin for the Imago software.
 * 
 * After compilation, put the jar file into the class path of the imago app.
 * 
 * The location of the plugin within the Imago menu is specified into the file:
 * <code>src/main/resources/plugins.config</code>.
 * 
 * @author dlegland
 */
public class HelloImagoPlugin implements FramePlugin
{
    /**
     * Empty constructor.
     */
    public HelloImagoPlugin()
    {
        System.out.println("Create HelloImago plugin");
    }

    public void run(ImagoFrame frame, String args)
    {
        System.out.println("Hello Imago!");
        frame.showMessage("Hello Imago!", "HelloImago Plugin");
    }
}
