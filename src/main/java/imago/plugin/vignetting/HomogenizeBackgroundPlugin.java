package imago.plugin.vignetting;

import java.util.Collection;

import imago.app.ImagoApp;
import imago.gui.GenericDialog;
import imago.gui.ImagoFrame;
import imago.gui.ImagoGui;
import imago.image.ImageFrame;
import imago.image.ImageHandle;
import imago.image.plugins.ImageFramePlugin;
import net.sci.algo.AlgoEvent;
import net.sci.array.Array;
import net.sci.array.Arrays;
import net.sci.array.binary.Binary;
import net.sci.array.binary.BinaryArray;
import net.sci.array.binary.BinaryArray2D;
import net.sci.array.numeric.Scalar;
import net.sci.array.numeric.ScalarArray;
import net.sci.array.numeric.ScalarArray2D;
import net.sci.image.Image;

/**
 * Computes a background estimate from an image, and combines it with the
 * original image to results in an enhanced image.
 * 
 * @author David Legland
 */
public class HomogenizeBackgroundPlugin implements ImageFramePlugin
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
        GenericDialog gd = new GenericDialog(frame, "Homogenize Background");
        gd.addChoice("Image: ", imageNameArray, firstImageName);
        gd.addChoice("Foreground Mask: ", imageNameArray, firstImageName);
        gd.addNumericField("Max Order: ", 2, 0);
        gd.addNumericField("Sampling Step: ", 2, 0);
        gd.addNumericField("Intensity Offset: ", 20, 2);
        
        // wait for user input
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
        double intensityOffset = gd.getNextNumber();
        
        
        // extract arrays and check dimensions
        Array<?> array = refImage.getData();
        Array<?> mask = maskImage.getData();
        if (array.dimensionality() != 2 || mask.dimensionality() != 2)
        {
            ImagoGui.showErrorDialog(frame, "Requires images with dimensionality 2", "Dimension Error");
            return;
        }
        if (!Arrays.isSameSize(array, mask))
        {
            ImagoGui.showErrorDialog(frame, "Both images must have same size", "Dimension Error");
            return;
        }
        if (!Scalar.class.isAssignableFrom(array.elementClass()))
        {
            ImagoGui.showErrorDialog(frame, "Reference image must contain scalar or RGB8 data\nCurrent class is: " + array.elementClass().getName(), "Image Type Error");
            return;
        }
        if (mask.elementClass() != Binary.class)
        {
            ImagoGui.showErrorDialog(frame, "Mask image must contain binary data", "Image Type Error");
            return;
        }
        
        frame.algoStatusChanged(new AlgoEvent(this, "Start Vignetting removal"));
        BinaryArray2D bgMask = BinaryArray2D.wrap((BinaryArray.wrap(mask)));
        
        Array<?> corrected;
        frame.algoStatusChanged(new AlgoEvent(this, "Start Background normalization"));
        if (Scalar.class.isAssignableFrom(array.elementClass()))
        {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            ScalarArray2D<?> array2d = ScalarArray2D.wrapScalar2d(ScalarArray.wrap((Array<? extends Scalar>) array));
            ScalarArray2D<?> bgFit = FitBackgroundPlugin.fitBackground(array2d, bgMask, maxOrder, samplingStep);

            int sizeX = array2d.size(0);
            int sizeY = array2d.size(1);
            ScalarArray2D<?> corr = ScalarArray2D.wrapScalar2d(array2d.newInstance(sizeX, sizeY));
            corr.fillValue(intensityOffset);
            
            for (int y = 0; y < sizeY; y++)
            {
                for (int x = 0; x < sizeX; x++)
                {
                    double v = array2d.getValue(x, y);
                    double bg = bgFit.getValue(x, y);
                    corr.setValue(x, y, v - bg + intensityOffset);
                }
            }
            corrected = corr;
        }
        else
        {
            throw new RuntimeException("Can not process array with element class: " + array.elementClass().getName());
        }
        
        Image result = new Image(corrected, refImage);
        result.setName(refImage.getName() + "-bgHom");
        
        // add the image document to GUI
        ImageFrame.create(result, frame);
    }

}
