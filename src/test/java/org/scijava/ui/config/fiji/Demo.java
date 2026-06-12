package org.scijava.ui.config.fiji;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.frame.Recorder;

public class Demo
{

	public static void main( final String[] args )
	{
		try
		{
			ImageJ.main( args );

			// Switch on macro recorder.
			new Recorder();

			final ImagePlus imp = IJ.openImage( "http://imagej.net/images/blobs.gif" );
			imp.show();

			new MyCellpose3Plugin().run( "" );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}
}
