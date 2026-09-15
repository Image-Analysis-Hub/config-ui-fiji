/*-
 * #%L
 * A Java library to facilitate building user-interfaces for simple Fiji plugins.
 * %%
 * Copyright (C) 2026 Institut Pasteur
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the Institut Pasteur nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.scijava.ui.config.fiji;

import java.awt.Rectangle;

import org.scijava.command.Previewable;
import org.scijava.ui.config.Configurator;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.Duplicator;

/**
 * A {@link ConfigFijiPlugin} that implements a default preview. This default
 * preview consists in running the process on the current time-point of the
 * source image, possibly cropped by the ROI if it has one.
 * Subclasses must implement the {@link #process(ImagePlus, int)} method to
 * perform the actual processing of the image.
 *
 * @author Jean-Yves Tinevez
 *
 * @param <C>
 *            the type of {@link Configurator} used to build the UI.
 */
public abstract class ConfigFijiPluginPreviewable< C extends Configurator > extends ConfigFijiPlugin< C > implements Previewable
{

	@Override
	public void run()
	{
		process( imp, 0 );
		super.run();
	}

	@Override
	public void preview()
	{
		if ( imp == null )
			return;

		final Roi roi = imp.getRoi();
		final Duplicator dup = new Duplicator();
		final int z = imp.getSlice();
		final int t = imp.getFrame();
		final ImagePlus crop = dup.run( imp, 1, imp.getNChannels(), z, z, t, t );
		// Translate origin so that the ROIs are correctly positioned.
		if ( roi != null )
		{
			crop.getCalibration().xOrigin = roi.getBounds().x;
			crop.getCalibration().yOrigin = roi.getBounds().y;
			final Rectangle bounds = roi.getBounds();
			final Roi clone = ( Roi ) roi.clone();
			clone.translate( -bounds.x, -bounds.y );
			crop.setRoi( clone );
			// We need the ROI so that the outside of it are properly masked.
		}

		progress.indeterminate( false, "Starting preview..." );
		final int tOrigin = t - 1;
		try
		{
			process( crop, tOrigin );
		}
		finally
		{
			crop.changes = false;
			crop.close();
			imp.setRoi( roi );
			progress.clear();
			progress.message( "Preview done" );
		}
	}

	/**
	 * Hook for subclasses to implement the actual processing of the image. This
	 * method is called both for the final run and for the preview.
	 * <p>
	 * Important: the <code>input</code> image parameter may not be the original
	 * source image (the {@link ConfigFijiPlugin#imp} field), but a cropped copy
	 * of it. This happens when this method is called from the
	 * {@link #preview()}. In this case, the {@link Calibration#xOrigin}, ...
	 * contains the position of the crop in the source image, and the tOrigin
	 * parameter is the time-point the crop was extracted from in the source
	 * image. Also, the <code>tOrigin</code> parameter is the time-point in the
	 * source image from which the crop was extracted (0-based) This can be used
	 * to correctly display the results in the source image. See for instance
	 * the
	 * <code>ApposeUtils#addROIs( ImagePlus labels, String prefix, Color color, int tOrigin, boolean multipleChannels )</code>
	 * utility.
	 *
	 * @param input
	 *            the input image to process.
	 * @param tOrigin
	 *            in case the input image is a crop of the source image, this is
	 *            the time-point in the source image from which the crop was
	 *            extracted (0-based). If this method is called for the final
	 *            run, this parameter is always 0.
	 */
	protected abstract void process( ImagePlus input, int tOrigin );

	@Override
	public void cancel()
	{
		// By default we don't cancel the preview.
	}
}
