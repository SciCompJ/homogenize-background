package imago.plugin.vignetting;

import Jama.Matrix;
import Jama.QRDecomposition;
import net.sci.array.binary.BinaryArray2D;
import net.sci.array.numeric.ScalarArray2D;

/**
 * 
 */

/**
 * Fit a polynomial surface to the background of an image.
 * 
 * @author David Legland
 *
 */
public class FitPolynomialBackground
{
    // =============================================================
    // Class variables

    int maxOrder;
    double[] coeffs;

    int[] xPowers;
    int[] yPowers;

    double[][] xMonoms;
    double[][] yMonoms;

    int samplingStep;
    
    
    // =============================================================
    // Constructors

    /**
     * Constructs a new polynomial background fitter with the given order.
     * 
     * @param maxOrder
     *            the maximal order of x and y coefficients
     */
    public FitPolynomialBackground(int maxOrder)
    {
        this.maxOrder = maxOrder;
        initXYPowers();
    }

    public void setMaxOrder(int maxOrder)
    {
        this.maxOrder = maxOrder;
        initXYPowers();
    }

    public void setSamplingStep(int samplingStep)
    {
        this.samplingStep = samplingStep;
    }
    
    /**
     * Compute x and y powers depending on the polynomial order. Also allocates
     * memory for coefficients.
     */
    private void initXYPowers()
    {
        // computes number of coefficients from max maxOrder.
        // Uses Gauss's relation
        int nCoeffs = (this.maxOrder + 1) * (this.maxOrder + 2) / 2;

        // allocate memory for coefficient array
        this.coeffs = new double[nCoeffs];

        // powers of x and y
        this.xPowers = new int[nCoeffs];
        this.yPowers = new int[nCoeffs];

        // initialize the arrays of x and y powers
        int c = 1;
        for (int order = 1; order <= this.maxOrder; order++)
        {
            for (int n = 0; n <= order; n++)
            {
                this.xPowers[c] = order - n;
                this.yPowers[c] = n;
                c++;
            }
        }
    }

    
    // =============================================================
    // Processing methods

    /**
     * Estimate the background coefficient from a given couple image+mask, and
     * returns the estimated background image.
     * 
     * @param image
     *            input image containing the background and the foreground
     * @param mask
     *            binary image corresponding to the background
     * @return an estimate of the background image
     */
    public ScalarArray2D<?> process(ScalarArray2D<?> array, BinaryArray2D mask)
    {
        // double t0 = System.currentTimeMillis();
        // Fit background coefficients
        fit(array, mask, samplingStep);
        // double t1 = System.currentTimeMillis();
        // System.out.println("Fit coeffs.: " + (t1 - t0) + " ms");

        // image dimensions
        int sizeX = array.size(0);
        int sizeY = array.size(1);
        
        // allocate memory for result
        ScalarArray2D<?> result = ScalarArray2D.wrap(array.newInstance(new int[] { sizeX, sizeY }));

        // double t2 = System.currentTimeMillis();
        // System.out.println("Create image: " + (t2 - t1) + " ms");

        fillImage(result);
        // double t3 = System.currentTimeMillis();
        // System.out.println("Fill: " + (t3 - t2) + " ms");

        return result;
    }

    public void fit(ScalarArray2D<?> array, BinaryArray2D mask, int samplingStep)
    {
        // number of coefficients
        int nCoeffs = this.coeffs.length;

        // image dimensions
        int sizeX = array.size(0);
        int sizeY = array.size(1);

        // first count the number of points within the mask,
        // in order to known the size of the matrix
        int nValues = 0;
        for (int y = 0; y < sizeY; y += samplingStep)
        {
            for (int x = 0; x < sizeX; x += samplingStep)
            {
                if (mask.getBoolean(x, y)) nValues++;
            }
        }

        // pre-compute x and y monoms
        computeMonoms(sizeX, sizeY);

        // initialize matrices A and b
        Matrix matrix, values;
        try
        {
            matrix = new Matrix(nValues, nCoeffs);
            values = new Matrix(nValues, 1);
        }
        catch (Exception ex)
        {
            throw new RuntimeException("Could not allocate memory for matrix with " + nValues
                    + " values and " + nCoeffs + " coefficients");
        }

        // iterate over each pixel to initialize both matrix and vector of
        // values
        int i = 0;
        for (int y = 0; y < sizeY; y += samplingStep)
        {
            for (int x = 0; x < sizeX; x += samplingStep)
            {
                // do not process values outside mask
                if (!mask.getBoolean(x, y)) continue;

                // init matrix
                for (int c = 0; c < nCoeffs; c++)
                {
                    matrix.set(i, c, xMonoms[x][c] * yMonoms[y][c]);
                }

                // init vector of values to fit
                values.set(i, 0, array.getValue(x, y));

                i++;
            }
        }

        // find least squares solution
        QRDecomposition qr = new QRDecomposition(matrix);
        Matrix theta = qr.solve(values);

        // extract coefficients
        for (int c = 0; c < nCoeffs; c++)
            coeffs[c] = theta.get(c, 0);
    }

    /**
     * Fills the given image processor with the background estimate of each
     * position.
     * 
     * @param array
     *            the array to fill
     */
    public void fillImage(ScalarArray2D<?> array)
    {
        int nCoeffs = coeffs.length;

        int sizeX = array.size(0);
        int sizeY = array.size(1);

        computeMonoms(sizeX, sizeY);

        // iterate over each pixel to initialize both matrix and vector of
        // values
        for (int y = 0; y < sizeY; y++)
        {
            for (int x = 0; x < sizeX; x++)
            {
                // compute value at current position
                double value = 0;
                for (int c = 0; c < nCoeffs; c++)
                {
                    value += coeffs[c] * xMonoms[x][c] * yMonoms[y][c];
                }

                array.setValue(x, y, value);
            }
        }
    }

    /**
     * Pre-compute the monoms used for fitting the polynomial function.
     * 
     * @param width
     * @param height
     */
    private void computeMonoms(int width, int height)
    {
        int nCoeffs = coeffs.length;

        xMonoms = new double[width][nCoeffs];
        for (int x = 0; x < width; x++)
        {
            double x2 = normalizePosition(x, width);
            for (int c = 0; c < nCoeffs; c++)
                xMonoms[x][c] = Math.pow(x2, xPowers[c]);
        }

        yMonoms = new double[height][nCoeffs];
        for (int y = 0; y < height; y++)
        {
            double y2 = normalizePosition(y, height);
            for (int c = 0; c < nCoeffs; c++)
                yMonoms[y][c] = Math.pow(y2, yPowers[c]);
        }
    }

    /**
     * Converts a position given as an index between 0 and a given size, into a
     * position between -1.5 and +1.5, better suited for numerical computations
     * involving powers.
     */
    private static double normalizePosition(int index, int size)
    {
        return (((double) index / (double) size) - .5) * 3;
    }
}
