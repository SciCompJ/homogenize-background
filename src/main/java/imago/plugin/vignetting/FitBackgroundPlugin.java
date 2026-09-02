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
import net.sci.array.color.RGB8;
import net.sci.array.color.RGB8Array;
import net.sci.array.color.RGB8Array2D;
import net.sci.array.numeric.Float32Array2D;
import net.sci.array.numeric.Scalar;
import net.sci.array.numeric.ScalarArray;
import net.sci.array.numeric.ScalarArray2D;
import net.sci.image.Image;
import net.sci.image.ImageType;

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
        gd.addChoice("Background Mask: ", imageNameArray, firstImageName);
        gd.addNumericField("Max Order: ", 2, 0);
        gd.addNumericField("Sampling Step: ", 2, 0);
        gd.addCheckBox("Show Residual Map", true);
        
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
        boolean showResiduals = gd.getNextBoolean();
        
        
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
        if (!Scalar.class.isAssignableFrom(array.elementClass()) && array.elementClass() != RGB8.class)
        {
            ImagoGui.showErrorDialog(frame, "Reference image must contain scalar or RGB8 data\nCurrent class is: " + array.elementClass().getName(), "Image Type Error");
            return;
        }
        if (mask.elementClass() != Binary.class)
        {
            ImagoGui.showErrorDialog(frame, "Mask image must contain binary data", "Image Type Error");
            return;
        }
        
        if (showResiduals && !Scalar.class.isAssignableFrom(array.elementClass()))
        {
            ImagoGui.showErrorDialog(frame, "Can not compute residuals of non scalar images", "Image Type Error");
            return;
        }
        
        frame.algoStatusChanged(new AlgoEvent(this, "Start Vignetting removal"));
        BinaryArray2D bgMask = BinaryArray2D.wrap((BinaryArray.wrap(mask)));
        
        Array<?> bgFit;
        frame.algoStatusChanged(new AlgoEvent(this, "Start Background normalization"));
        if (Scalar.class.isAssignableFrom(array.elementClass()))
        {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            ScalarArray2D<?> array2d = ScalarArray2D.wrapScalar2d(ScalarArray.wrap((Array<? extends Scalar>) array));
            bgFit = fitBackground(array2d, bgMask, maxOrder, samplingStep);

            if (showResiduals)
            {
                ScalarArray2D<?> resid = residuals((ScalarArray2D<?>) bgFit, array2d, bgMask);
                Image residImage = new Image(resid, ImageType.DIVERGING, refImage);
                residImage.setName(refImage.getName() + "-bgFit-resid");
                double[] valueRange = resid.finiteValueRange();
                double maxAbsDiff = Math.max(valueRange[0], valueRange[1]);
                residImage.getDisplaySettings().setDisplayRange(new double[] {-maxAbsDiff, maxAbsDiff});
                ImageFrame.create(residImage, frame);
            }
        }
        else if (array.elementClass() == RGB8.class)
        {
            RGB8Array2D array2d = RGB8Array2D.wrap(RGB8Array.wrap(array));
            bgFit = fitBackground_rgb(array2d, bgMask, maxOrder, samplingStep);
        }
        else
        {
            throw new RuntimeException("Can not process array with element class: " + array.elementClass().getName());
        }
        
        Image result = new Image(bgFit, refImage);
        result.setName(refImage.getName() + "-bgFit");
        
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
    
    public static final RGB8Array2D fitBackground_rgb(RGB8Array2D image, BinaryArray2D mask,
            int orderMax, int samplingStep)
    {
        FitPolynomialBackground pbg = new FitPolynomialBackground(orderMax);
        pbg.setSamplingStep(samplingStep);
        RGB8Array2D res = RGB8Array2D.create(image.size(0), image.size(1));
        for (int c = 0; c < 3; c++)
        {
            res.setChannel(c, pbg.process(image.channel(c), mask));
        }
        return res;
    }
    
    private static final ScalarArray2D<?> residuals(ScalarArray2D<?> fitted, ScalarArray2D<?> image, BinaryArray2D mask)
    {
        int sizeX = fitted.size(0);
        int sizeY = fitted.size(1);

        Float32Array2D res = Float32Array2D.create(sizeX, sizeY);

        // iterate over pixels
        for (int y = 0; y < sizeY; y++)
        {
            for (int x = 0; x < sizeX; x++)
            {
                if (mask.getBoolean(x, y))
                {
                    res.setValue(x, y, fitted.getValue(x, y) - image.getValue(x, y));
                }
                else
                {
                    res.setValue(x, y, Float.NaN);
                }
            }
        }
        
        return res;
    }

}
