package imago.plugin.vignetting;

import java.util.Collection;

import imago.app.ImagoApp;
import imago.gui.GenericDialog;
import imago.gui.ImagoFrame;
import imago.gui.ImagoGui;
import imago.image.ImageFrame;
import imago.image.ImageHandle;
import imago.image.plugin.ImageFramePlugin;
import net.sci.array.Array;
import net.sci.array.Arrays;
import net.sci.array.binary.BinaryArray;
import net.sci.array.binary.BinaryArray2D;
import net.sci.array.numeric.ScalarArray;
import net.sci.array.numeric.ScalarArray2D;
import net.sci.image.Image;

/**
 * Fit a polynomial surface to the background of an image.
 * 
 * @author David Legland
 */
public class FitBackgroundPlugin implements ImageFramePlugin
{
    /*
     * (non-Javadoc)
     * 
     * @see ij.plugin.PlugIn#run(java.lang.String)
     */
    @Override
    public void run(ImagoFrame frame, String arg)
    {
        ImagoGui gui = frame.getGui();
        ImagoApp app = gui.getAppli();
        
        // Retrieve name of open images
        Collection<String> imageNames = ImageHandle.getAllNames(app);
        if (imageNames.size() == 0)
        {
            return;
        }
        
        String[] imageNameArray = imageNames.toArray(new String[]{});
        String firstImageName = imageNameArray[0];
                
        // Creates the dialog
        GenericDialog gd = new GenericDialog(frame, "Fit Background");
        gd.addChoice("Image: ", imageNameArray, firstImageName);
        gd.addChoice("Foreground Mask: ", imageNameArray, firstImageName);
        gd.addNumericField("Max Order: ", 2, 0);
        gd.addNumericField("Sampling Step: ", 2, 0);
        
        // wait for user inptu
        gd.showDialog();
        if (gd.wasCanceled()) 
        {
            return;
        }
        
        // parse dialog results
        Image refImage = ImageHandle.findFromName(app, gd.getNextChoice()).getImage();
        Image maskImage = ImageHandle.findFromName(app, gd.getNextChoice()).getImage();
        int maxOrder = (int) gd.getNextNumber();
        int samplingStep = (int) gd.getNextNumber();

        // extract arrays and check dimensions
        Array<?> array = refImage.getData();
        Array<?> mask = maskImage.getData();
        if (array.dimensionality()!=2 || mask.dimensionality()!=2)
        {
            ImagoGui.showErrorDialog(frame, "Requires images with dimensionality 2", "Dimension Error");
            return;
        }
        if (!Arrays.isSameSize(array, mask))
        {
            ImagoGui.showErrorDialog(frame, "Both images must have same size", "Dimension Error");
            return;
        }
        if (!(array instanceof ScalarArray))
        {
            ImagoGui.showErrorDialog(frame, "Reference image must be a scalar image", "Image Type Error");
            return;
        }
        if (!(mask instanceof BinaryArray))
        {
            ImagoGui.showErrorDialog(frame, "Mask image must be a binary image", "Image Type Error");
            return;
        }
        
        // IJ.showStatus("Start Vignetting removal");
        BinaryArray2D bgMask = BinaryArray2D.wrap(((BinaryArray) mask).complement());
        
//        IJ.showStatus("Start Background normalisation");
        ScalarArray2D<?> bgFit = fitBackground((ScalarArray2D<?>) array, bgMask, maxOrder, samplingStep);

        Image result = new Image(bgFit, refImage);
        result.setName(refImage.getName() + "-background");

        // add the image document to GUI
        ImageFrame.create(result, frame);
    }

    public static final ScalarArray2D<?> fitBackground(ScalarArray2D<?> image, BinaryArray2D mask,
            int orderMax)
    {
        return fitBackground(image, mask, orderMax, 1);
    }

    public static final ScalarArray2D<?> fitBackground(ScalarArray2D<?> image, BinaryArray2D mask,
            int orderMax, int samplingStep)
    {
        FitPolynomialBackground pbg = new FitPolynomialBackground(orderMax);
        pbg.setSamplingStep(samplingStep);
        return pbg.process(image, mask);
    }
}
