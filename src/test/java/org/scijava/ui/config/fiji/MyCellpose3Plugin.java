package org.scijava.ui.config.fiji;

import org.scijava.Cancelable;
import org.scijava.command.Previewable;
import org.scijava.ui.config.visitors.Strings;
import org.scijava.ui.config.visitors.gui.FrameBuilder.ConfigFrame.Progress;

import ij.IJ;
import ij.ImagePlus;

public class MyCellpose3Plugin extends ConfigFijiPlugin< Cellpose3Config > implements Cancelable, Previewable
{

	private String cancelReason;

	@Override
	public Cellpose3Config createConfig( final ImagePlus imp )
	{
		final int nChannels = imp.getNChannels();
		final double pixelSize = imp.getCalibration().pixelWidth;
		final String units = imp.getCalibration().getUnits();
		return new Cellpose3Config( nChannels, pixelSize, units );
	}

	@Override
	public void run( final Progress progress ) throws Exception
	{
		cancelReason = null;
		IJ.log( "Running Cellpose3 on image " + getImagePlus().getTitle() + " with config:" );
		IJ.log( Strings.toString( getConfig() ) );
		IJ.log( "Pretending to run Cellpose3..." );
		final int max = 25;
		int i = max;
		while ( i-- > 0 && !isCanceled() )
		{
			Thread.sleep( 100 );
			progress.set( ( max - i ) / ( double ) max, "Running Cellpose 3" );
		}
		progress.clear();

		if ( isCanceled() )
		{
			IJ.log( "Canceled: " + getCancelReason() );
			return;
		}
		super.run( progress );
		IJ.log( "Done!" );
	}

	@Override
	public boolean isCanceled()
	{
		return cancelReason != null;
	}

	@Override
	public void cancel( final String reason )
	{
		this.cancelReason = reason;
	}

	@Override
	public String getCancelReason()
	{
		return cancelReason;
	}

	@Override
	public void preview()
	{
		cancelReason = null;
		IJ.log( "Previewing Cellpose3 on current plane of " + getImagePlus().getTitle() );
		final int max = 25;
		int i = max;
		while ( i-- > 0 && !isCanceled() )
		{
			try
			{
				Thread.sleep( 100 );
			}
			catch ( final InterruptedException e )
			{
				e.printStackTrace();
			}
		}
		if ( isCanceled() )
		{
			IJ.log( "Preview canceled: " + getCancelReason() );
			return;
		}
		IJ.log( "Preview done!" );
	}

	@Override
	public void cancel()
	{
		cancel( "User canceled preview." );
	}
}
