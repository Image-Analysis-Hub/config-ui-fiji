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

import static org.scijava.ui.config.fiji.PostProcessUtils.addROIs;
import static org.scijava.ui.config.fiji.PostProcessUtils.clearOutsideRoi;
import static org.scijava.ui.config.fiji.PostProcessUtils.transferCalibration;

import java.awt.Color;
import java.util.List;

import org.scijava.ui.config.Configurator;
import org.scijava.ui.config.Parameters.BooleanParam;
import org.scijava.ui.config.Parameters.EnumParam;
import org.scijava.ui.config.Parameters.Parameter;
import org.scijava.ui.config.fiji.visitors.IJMacro;
import org.scijava.ui.config.utils.GuiUtils;
import org.scijava.ui.config.visitors.Prefs;
import org.scijava.ui.config.visitors.gui.FrameBuilder;
import org.scijava.ui.config.visitors.gui.FrameBuilder.ConfigFrame;
import org.scijava.ui.config.visitors.gui.Progress;
import org.scijava.ui.config.visitors.gui.ProgressAware;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.RoiManager;

public abstract class ConfigFijiPlugin< C extends Configurator > implements PlugIn, Runnable, ProgressAware
{

	/**
	 * The config instance, modified by the UI and recorded in the macro.
	 */
	protected C config;

	/**
	 * The active image, on which the plugin is called.
	 */
	protected ImagePlus imp;

	protected Progress progress;

	@Override
	public void setProgress( final Progress progress )
	{
		this.progress = progress;
	}

	@Override
	public void run( final String arg )
	{
		final String macroOptions = Macro.getOptions();
		this.imp = WindowManager.getCurrentImage();
		this.config = createConfig( imp );

		if ( macroOptions != null )
		{
			// ----------------------------------------------------------
			// Macro / IJ.run() path — skip the UI entirely
			// -> parse the options string and fill the config with the values
			// ----------------------------------------------------------
			runViaMacro( macroOptions );
		}
		else
		{
			// ----------------------------------------------------------
			// Interactive path — show the UI
			// ----------------------------------------------------------
			showUI();
		}
	}

	private void runViaMacro( final String macroOptions )
	{
		IJMacro.optionsToConfig( macroOptions, config );
		try
		{
			progress = new IJProgress();
			run();
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}

	protected ConfigFrame showUI()
	{
		// Load previously saved values.
		Prefs.deserialize( config );
		// Shows the UI and return.
		final ConfigFrame frame = FrameBuilder.build( config, this, createConfig( imp ) );
		if ( imp.getWindow() != null )
		{
			GuiUtils.positionWindow( frame, imp.getWindow() );
			imp.getWindow().addWindowListener( new java.awt.event.WindowAdapter()
			{
				@Override
				public void windowClosed( final java.awt.event.WindowEvent e )
				{
					frame.dispose();
				}
			} );
		}
		final String title = frame.getTitle();
		frame.setTitle( title + " - " + imp.getTitle() );
		frame.setVisible( true );
		return frame;
	}

	/**
	 * Hook for subclasses to implement the actual plugin logic. The method
	 * called when the user clicks "plays" in the UI or when the plugin is run
	 * from a macro. The config object will have been updated with the values
	 * from the UI or from the macro options string, respectively.
	 * <p>
	 * Note that to make the plugin macro-recordable, subclassers must call
	 * <code>super.run()</code> at the end of this method, which will record the
	 * macro with the current config values.
	 *
	 */
	@Override
	public void run()
	{
		recordMacro( config );
	}

	/**
	 * Utility method that can be called by subclasses to post-process the
	 * output image, by transferring the calibration from the input image and
	 * clearing pixels outside the ROI.
	 *
	 * @param input
	 *            the input image, from which to transfer the calibration and
	 *            ROI. Will not be modified.
	 * @param toPostProcess
	 *            the output image to post-process. Will be modified in place.
	 * @param inputRoi
	 */
	protected static void postProcessOuput( final ImagePlus input, final ImagePlus toPostProcess )
	{
		Roi inputRoi = input.getRoi();
		if ( inputRoi != null )
			inputRoi = ( Roi ) inputRoi.clone();
		transferCalibration( input, toPostProcess, inputRoi );
		clearOutsideRoi( toPostProcess, inputRoi );
	}

	/**
	 * Creates and and show a list of ROIs in the ROI manager, from a label
	 * image. The ROIs are named with the given prefix, and their time-point is
	 * shifted by the specified time origin. The input image is used to
	 * determine whether the ROIs should be displayed on all channels or only on
	 * the current channel.
	 * <p>
	 * ROIs are created only for 2D images.
	 *
	 * @param input
	 *            the input image from which the label image was generated.
	 * @param labels
	 *            the label image to create ROIs from.
	 * @param prefix
	 *            the prefix to use for naming the ROIs.
	 * @param tOrigin
	 *            the time origin for the ROIs.
	 */
	protected static void toROIs( final ImagePlus input, final ImagePlus labels, final String prefix, final int tOrigin )
	{
		final boolean multipleChannels = input.getNChannels() > 1;
		addROIs( labels, prefix, Color.YELLOW, tOrigin, multipleChannels );
		RoiManager.getInstance2().runCommand( "Show All" );
	}

	private void recordMacro( final C config )
	{
		if ( !Recorder.record )
			return;
		Recorder.setCommand( config.getName() );

		final List< Parameter< ?, ? > > params = config.getSelectedParameters();
		for ( final Parameter< ?, ? > param : params )
		{
			if ( param instanceof BooleanParam )
			{
				if ( ( Boolean ) param.getValue() )
					Recorder.recordOption( param.getKey() );
			}
			else if ( param instanceof EnumParam )
			{
				Recorder.recordOption( param.getKey(), ( ( Enum< ? > ) param.getValue() ).name() );
			}
			else
			{
				Recorder.recordOption( param.getKey(), param.getValue().toString() );
			}
		}
		Recorder.saveCommand();
	}

	/**
	 * Creates a new instance of the configuration object, which will be used to
	 * show the UI and to record the macro.
	 *
	 * @param imp
	 *            the active ImagePlus, which can be used to initialize the
	 *            config with image-specific values (e.g. pixel size, number of
	 *            channels, etc.).
	 */
	protected abstract C createConfig( final ImagePlus imp );

	/**
	 * When the plugin is run from a macro, report progress via the IJ toolbar.
	 */
	public static class IJProgress implements Progress
	{

		@Override
		public void set( final double fraction )
		{
			IJ.showProgress( fraction );

		}

		@Override
		public void set( final double fraction, final String text )
		{
			set( fraction );
			message( text );
		}

		@Override
		public void indeterminate( final boolean on, final String text )
		{
			if ( !on )
				set( 0. );
			message( text );
		}

		@Override
		public void message( final String text )
		{
			IJ.showStatus( text );
		}

		@Override
		public void clear()
		{
			set( 0. );
			message( "" );
		}

		@Override
		public boolean isCanceled()
		{
			return false;
		}
	}
}
