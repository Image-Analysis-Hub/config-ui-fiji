package org.scijava.ui.config.fiji;

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
import org.scijava.ui.config.visitors.gui.FrameBuilder.ConfigFrame.Progress;
import org.scijava.ui.config.visitors.gui.FrameBuilder.UserTask;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;

public abstract class ConfigFijiPlugin< C extends Configurator > implements PlugIn, UserTask
{

	/**
	 * The config instance, modified by the UI and recorded in the macro.
	 */
	protected C config;

	/**
	 * The active image, on which the plugin is called.
	 */
	protected ImagePlus imp;

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
			IJMacro.optionsToConfig( macroOptions, config );
			try
			{
				this.run( new IJProgress() );
			}
			catch ( final Exception e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			// ----------------------------------------------------------
			// Interactive path — show the UI
			// ----------------------------------------------------------
			
			// Load previously saved values.
			Prefs.deserialize( config );
			// Shows the UI and return.
			final ConfigFrame frame = FrameBuilder.build( config, this, createConfig( imp ) );
			if ( imp.getWindow() != null )
				GuiUtils.positionWindow( frame, imp.getWindow() );
			final String title = frame.getTitle();
			frame.setTitle( title + " - " + imp.getTitle() );
			frame.setVisible( true );
		}
	}

	/**
	 * Hook for subclasses to implement the actual plugin logic. The method
	 * called when the user clicks "plays" in the UI or when the plugin is run
	 * from a macro. The config object will have been updated with the values
	 * from the UI or from the macro options string, respectively.
	 * <p>
	 * Note that to make the plugin macro-recordable, subclassers must call
	 * <code>super.run(progress)</code> at the end of this method, which will
	 * record the macro with the current config values.
	 * 
	 */
	@Override
	public void run( final Progress progress ) throws Exception
	{
		recordMacro( config );
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
	protected static class IJProgress implements Progress
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
