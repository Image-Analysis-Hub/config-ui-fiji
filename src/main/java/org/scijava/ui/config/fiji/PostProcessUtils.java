package org.scijava.ui.config.fiji;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.scijava.ui.config.fiji.roi.LabelMapToPolygons;
import org.scijava.ui.config.fiji.roi.Polygon2D;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.LUT;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJVirtualStack;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;

/**
 * Utilities for post-processing the outputs of a Fiji plugin for the classical
 * segmentation workflow. That is: most of the methods you will find here are
 * suitable when you need to post-process the outputs of a segmentation plugin,
 * that e.g. returns a label image as output.
 *
 * @author Jean-Yves Tinevez
 *
 */
public class PostProcessUtils
{

	/**
	 * Clear pixels outside the ROI.
	 */
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public static final void clearOutsideRoi( final ImagePlus imp, final Roi roi )
	{
		if ( roi == null )
			return;

		// shift the roi in the crop image size (outputs are cropped images)
		final Rectangle bounds = roi.getBounds();
		roi.translate( -bounds.x, -bounds.y );

		try
		{
			final ImageStack stack = imp.getStack();
			if ( stack instanceof ImageJVirtualStack )
			{

				// This is an imglib2-backed virtual stack, so we must use
				// imglib2 to clear outside the ROI.
				final RandomAccessibleInterval< NumericType > img = getRAIFromStack( stack );
				clearOutsideRoi( img, roi );
			}
			else
			{
				// Classical ImageJ stuff.
				for ( int z = 1; z <= stack.getSize(); z++ )
				{
					final ImageProcessor ip = stack.getProcessor( z );
					ip.setValue( 0 );
					ip.fillOutside( roi );
				}
			}
		}
		catch ( final Exception e )
		{
			IJ.error( "Failed to clear outside ROI: " + e.getMessage() );
		}
		finally
		{
			// put to the ROI back to original image size
			roi.translate( bounds.x, bounds.y );
		}
	}

	private static final < T extends NumericType< T > > void clearOutsideRoi( final RandomAccessibleInterval< T > img, final Roi roi )
	{
		if ( roi == null )
			return;

		// Make a 2D mask.
		final int n = img.numDimensions();
		final long minX = img.min( 0 ), minY = img.min( 1 );
		final Img< BitType > mask2d = ArrayImgs.bits( img.dimension( 0 ), img.dimension( 1 ) );
		final Cursor< BitType > mc = mask2d.localizingCursor();
		while ( mc.hasNext() )
		{
			mc.fwd();
			final int x = ( int ) ( minX + mc.getLongPosition( 0 ) );
			final int y = ( int ) ( minY + mc.getLongPosition( 1 ) );
			mc.get().set( !roi.contains( x, y ) );
		}

		// Broadcast it to the nD of the image
		RandomAccessible< BitType > mask = Views.translate( mask2d, minX, minY );
		for ( int d = 2; d < n; ++d )
			mask = Views.addDimension( mask );
		final RandomAccessibleInterval< BitType > maskND = Views.interval( mask, img );

		// Clear when the mask is true.
		LoopBuilder.setImages( img, maskND ).multiThreaded().forEachPixel(
				( t, b ) -> {
					if ( b.get() )
						t.setZero();
				} );
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private static final RandomAccessibleInterval< NumericType > getRAIFromStack( final ImageStack stack )
	{
		if ( stack instanceof ImageJVirtualStack< ? > )
		{
			final ImageJVirtualStack virtualStack = ( ImageJVirtualStack< ? > ) stack;
			return virtualStack.getSource();
		}
		return null;
	}

	private static LUT loadLutFromResource( final String resourcePath )
	{
		try (InputStream is = PostProcessUtils.class.getResourceAsStream( resourcePath );
				BufferedReader reader = new BufferedReader( new InputStreamReader( is ) ))
		{

			if ( is == null )
			{
				IJ.error( "LUT resource not found: " + resourcePath );
				return null;
			}

			final byte[] reds = new byte[ 256 ];
			final byte[] greens = new byte[ 256 ];
			final byte[] blues = new byte[ 256 ];
			String line;
			int index = 0;

			while ( ( line = reader.readLine() ) != null && index < 256 )
			{
				line = line.trim();
				if ( line.isEmpty() )
					continue; // Skip empty lines

				// Split by whitespace
				final String[] parts = line.split( "\\s+" );
				if ( parts.length >= 3 )
				{
					reds[ index ] = ( byte ) Integer.parseInt( parts[ 0 ] );
					greens[ index ] = ( byte ) Integer.parseInt( parts[ 1 ] );
					blues[ index ] = ( byte ) Integer.parseInt( parts[ 2 ] );
					index++;
				}
			}

			if ( index != 256 )
			{
				IJ.error( "Invalid LUT file: expected 256 entries, found " + index );
				return null;
			}

			return new LUT( reds, greens, blues );
		}
		catch ( final IOException e )
		{
			IJ.error( "Failed to load LUT: " + e.getMessage() );
			return null;
		}
	}

	public static final void useGlasbeyDarkLUT( final ImagePlus imp )
	{
		final LUT lut = loadLutFromResource( "glasbey_on_dark.lut" );
		useLUT( imp, lut );
	}

	public static final void useLUT( final ImagePlus imp, final LUT lut )
	{
		imp.setLut( lut );
		imp.updateAndDraw();
	}

	/**
	 * Creates a list of ImageJ ROIs from a label image and adds them to the ROI
	 * manager. The ROIs are {@link PolygonRoi}s.
	 *
	 * @param labels
	 *            the label image to create ROIs from. Important: the ROIs are
	 *            created at coordinates relative to the calibration.xOrigin and
	 *            calibration.yOrigin of this label image, so that they are the
	 *            right position if the label image was generated from a crop
	 *            view of the input.
	 * @param prefix
	 *            the prefix to use for naming the ROIs.
	 * @param color
	 *            the color to use for the ROIs. If <code>null</code>, the
	 *            default color will be used.
	 * @param tOrigin
	 *            the time origin for the ROIs. ROIs with frame 1 will be
	 *            displayed at frame tOrigin +1, etc.
	 * @param multipleChannels
	 *            set it to <code>true</code> if the target image has multiple
	 *            channels. Otherwise the ROIs will be displayed on all frames
	 *            of the target image.
	 * @return a list of ROIs corresponding to the labels in the input image.
	 */
	public static void addROIs( final ImagePlus labels, final String prefix, final Color color, final int tOrigin, final boolean multipleChannels )
	{
		final RoiManager rm = RoiManager.getRoiManager();
		toROIs( labels, prefix, color, tOrigin, multipleChannels ).forEach( rm::addRoi );
	}

	/**
	 * Converts a label image into a list of ImageJ ROIs. The ROIs are
	 * {@link PolygonRoi}s.
	 *
	 * @param labels
	 *            the label image to create ROIs from. Important: the ROIs are
	 *            created at coordinates relative to the calibration.xOrigin and
	 *            calibration.yOrigin of this label image, so that they are the
	 *            right position if the label image was generated from a crop
	 *            view of the input.
	 * @param prefix
	 *            the prefix to use for naming the ROIs.
	 * @param color
	 *            the color to use for the ROIs. If <code>null</code>, the
	 *            default color will be used.
	 * @param tOrigin
	 *            the time origin for the ROIs. ROIs with frame 1 will be
	 *            displayed at frame tOrigin + 1, etc.
	 * @param multipleChannels
	 *            set it to <code>true</code> if the target image has multiple
	 *            channels. Otherwise the ROIs will be displayed on all frames
	 *            of the target image.
	 * @return a list of ROIs corresponding to the labels in the input image.
	 */
	public static List< PolygonRoi > toROIs( final ImagePlus labels, final String prefix, final Color color, final int tOrigin, final boolean multipleChannels )
	{
		// We don't create ROIs for 3D images.
		if ( labels.getNSlices() > 1 )
			return Collections.emptyList();

		final List< PolygonRoi > rois = new ArrayList<>();
		final int nt = labels.getNFrames();
		final int nDigitsT = ( int ) Math.ceil( Math.log10( nt + 1 ) );

		for ( int t = 1; t <= nt; t++ )
		{
			final ImageProcessor image = labels.getImageStack().getProcessor( t );

			final int conn = 4;
			final LabelMapToPolygons.VertexLocation loc = LabelMapToPolygons.VertexLocation.CORNER;

			// compute boundaries
			final LabelMapToPolygons tracker = new LabelMapToPolygons( conn, loc );
			final Map< Integer, ArrayList< Polygon2D > > boundaries = tracker.process( image );
			final int nRois = boundaries.values().stream().mapToInt( List::size ).sum();
			final int nDigits = ( int ) Math.ceil( Math.log10( nRois + 1 ) );
			final String pattern = ( nt > 1 || tOrigin > 0 )
					? prefix + "_t%0" + nDigitsT + "d" + "_%0" + nDigits + "d"
					: prefix + "_%0" + nDigits + "d";

			int index = 1; // Start at 1 to match ImageJ ROI display

			/*
			 * There is some weirdness in ImageJ display of multiple ROIs from
			 * the ROI manager. If the target imp has multiple channels, and if
			 * you assign the ROI channel to 0, then the ROI is properly
			 * displayed on all channels, but only on its frame, as expected.
			 *
			 * BUT if the imp has only one channel, then the ROI is displayed on
			 * all frames, which is not what we want. The workaround is to
			 * assign the ROI channel to 1.
			 */
			final int targetChannel = multipleChannels
					? 0 // Show ROIs on all channels.
					: 1;
			for ( final int label : boundaries.keySet() )
			{
				final ArrayList< Polygon2D > polygons = boundaries.get( label );

				if ( polygons.size() <= 1 && nRois <= 1 )
				{
					final PolygonRoi roi = polygons.get( 0 ).createRoi();
					roi.translate( labels.getCalibration().xOrigin, labels.getCalibration().yOrigin );
					roi.setName( prefix );
					roi.setStrokeColor( color );
					roi.setPosition( targetChannel, 1, t + tOrigin );
					rois.add( roi );
				}
				else
				{
					for ( final Polygon2D poly : polygons )
					{
						final PolygonRoi roi = poly.createRoi();
						roi.translate( labels.getCalibration().xOrigin, labels.getCalibration().yOrigin );

						final String name = ( nt > 1 || tOrigin > 0 )
								? String.format( pattern, t + tOrigin, index++ )
								: String.format( pattern, index++ );
						roi.setPosition( targetChannel, 1, t + tOrigin );
						roi.setName( name );
						roi.setStrokeColor( color );
						rois.add( roi );
					}
				}
			}
		}
		return rois;
	}

	/**
	 * Transfers the calibration of an {@link ImagePlus} to another one,
	 * generated from a capture of the first one. Also is the specified ROI is
	 * not null, it will set the origin of the target image to the top-left
	 * corner of the bounding box of the ROI.
	 *
	 * @param from
	 *            the imp to copy from.
	 * @param to
	 *            the imp to copy to.
	 * @param initialRoi
	 */
	public static final void transferCalibration( final ImagePlus from, final ImagePlus to, final Roi initialRoi )
	{
		final Calibration fc = from.getCalibration();
		final Calibration tc = to.getCalibration();

		tc.setUnit( fc.getUnit() );
		tc.setTimeUnit( fc.getTimeUnit() );
		tc.frameInterval = fc.frameInterval;

		tc.pixelWidth = fc.pixelWidth;
		tc.pixelHeight = fc.pixelHeight;
		tc.pixelDepth = fc.pixelDepth;

		if ( initialRoi != null )
		{
			tc.xOrigin = fc.xOrigin + initialRoi.getBounds().x;
			tc.yOrigin = fc.yOrigin + initialRoi.getBounds().y;
		}
		else
		{
			tc.xOrigin = fc.xOrigin;
			tc.yOrigin = fc.yOrigin;
		}
	}
}
