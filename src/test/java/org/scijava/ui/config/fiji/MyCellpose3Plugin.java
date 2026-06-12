package org.scijava.ui.config.fiji;

import org.scijava.ui.config.visitors.Strings;
import org.scijava.ui.config.visitors.gui.FrameBuilder.ConfigFrame.Progress;

import ij.IJ;
import ij.ImagePlus;

public class MyCellpose3Plugin extends ConfigFijiPlugin< Cellpose3Config >
{

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
		IJ.log( "Running Cellpose3 on image " + imp.getTitle() + " with config:" );
		IJ.log( Strings.echo( config ) );
		IJ.log( "Pretending to run Cellpose3..." );
		final int max = 5;
		int i = max;
		while ( i-- > 0 )
		{
			Thread.sleep( 100 );
			progress.set( ( max - i ) / ( double ) max, "Running Cellpose 3" );
		}
		progress.clear();
		super.run( progress );
		IJ.log( "Done!" );
	}
}