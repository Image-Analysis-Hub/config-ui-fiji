package org.scijava.ui.config.fiji;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

public class DemoMacro
{

	@SuppressWarnings( "unchecked" )
	public static void main( final String[] args )
	{
		ImageJ.main( args );

		// Since this is a demo in the test folder, we need to register the
		// plugin manually, as it won't be picked up by the usual plugin
		// discovery mechanism.
		ij.Menus.getCommands().put( "Cellpose 3", "org.scijava.ui.config.fiji.MyCellpose3Plugin" );

		// Verify it worked
		System.out.println( "Registered: " + ij.Menus.getCommands().containsKey( "Cellpose 3" ) );

		final ImagePlus imp = IJ.openImage( "http://imagej.net/images/blobs.gif" );
		imp.show();
		final String options = "builtin_model=NUCLEI diameter=130.0 export_rois export_labels export_flows";

		IJ.log( "Running the macro with options: " + options );
		IJ.log( "" );

		IJ.run( "Cellpose 3", options );
	}
}
