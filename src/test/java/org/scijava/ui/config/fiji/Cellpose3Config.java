package org.scijava.ui.config.fiji;

import org.scijava.ui.config.Configurator;
import org.scijava.ui.config.Parameters.BooleanParam;
import org.scijava.ui.config.Parameters.DoubleParam;
import org.scijava.ui.config.Parameters.EnumParam;
import org.scijava.ui.config.Parameters.IntParam;
import org.scijava.ui.config.Parameters.PathParam;

public class Cellpose3Config extends Configurator
{

	private final EnumParam< Cellpose3BuiltinModels > builtinModel;

	private final PathParam customModel;

	private final SelectableParameters builtinOrCustom;

	private final DoubleParam diameter;

	private IntParam chan1;

	private IntParam chan2;

	private DoubleParam flowThreshold;

	private DoubleParam cellprobThreshold;

	private IntParam minSize;

	private BooleanParam exportROIs;

	private BooleanParam exportLabels;

	private BooleanParam exportFlows;

	public Cellpose3Config( final int nChannels, final double pixelSize, final String units )
	{
		super( "Cellpose 3", "https://imagej.net/plugins/cellpose-appose#usage" );

		// Choice among an enum.
		this.builtinModel = addEnumParameter( Cellpose3BuiltinModels.class )
				.key( "BUILTIN_MODEL" )
				.defaultValue( Cellpose3BuiltinModels.CYTO3 )
				.name( "Builtin model" )
				.help( "https://cellpose.readthedocs.io/en/v3.1.1.1/models.html#full-built-in-models" )
				.get();

		// File path.
		this.customModel = addPathParameter()
				.key( "CUSTOM_MODEL_PATH" )
				.defaultValue( "" ) // Better than null.
				.name( "Path to custom model" )
				.help( "Path to a custom Cellpose 3 model. " )
				.get();

		// One or the other, but not both.
		this.builtinOrCustom = addSelectableParameters()
				.key( "BUILTIN_OR_CUSTOM" )
				.add( builtinModel )
				.add( customModel )
				.get();

		// Channels, two int params.
		this.chan1 = addIntParameter()
				.key( "CHAN1" )
				.name( "Main channel" )
				.help( "The main channel to segment. Select 0 to use a grayscale blend of all channels." )
				.defaultValue( 1 )
				.min( 0 )
				.max( nChannels )
				.get();
		this.chan2 = addIntParameter()
				.key( "CHAN2" )
				.name( "Optional channel" )
				.help( "The second channel to segment. Select 0 to skip using a second channel." )
				.defaultValue( 0 )
				.min( 0 )
				.max( nChannels )
				.get();

		// Diameter param is in pixel, but we want to display it in physical
		// units. So we set a translator that converts between the two.

		this.diameter = addDoubleParameter()
				.key( "DIAMETER" )
				.name( "Diameter" )
				.help( "<html>Estimated diameter of objects, in physical units "
						+ "(stored in pixel size internally). " +
						"Set to 0 to let Cellpose estimate it automatically.</html>" )
				.units( units )
				.defaultValue( 30. )
				.min( 0. ) // But no max
				.get();

		setDisplayTranslator( diameter, d -> d * pixelSize, d -> d / pixelSize );

		/*
		 * Advanced parameters.
		 */

		this.flowThreshold = addDoubleParameter()
				.key( "FLOW_THRESHOLD" )
				.name( "Flow threshold" )
				.help( "<html>Threshold for flow error filtering. Lower = more masks (permissive), Higher = fewer masks (strict).</html>" )
				.defaultValue( 0.4 )
				.min( 0. )
				.max( 3. )
				.get();

		this.cellprobThreshold = addDoubleParameter()
				.key( "CELLPROB_THRESHOLD" )
				.name( "Cell probability threshold" )
				.help( "<html>Threshold for cell probability. Increase to filter low-confidence detections.</html>" )
				.defaultValue( 0.0 )
				.min( -6. )
				.max( 6. )
				.get();

		this.minSize = addIntParameter()
				.key( "MIN_SIZE" )
				.name( "Minimum size" )
				.help( "Objects smaller than this are removed." )
				.defaultValue( 15 )
				.min( 0 )
				.units( "pixels" )
				.get();

		addGroup( "Advanced parameters" )
				.add( flowThreshold )
				.add( cellprobThreshold )
				.add( minSize )
				.collapsed( true )
				.get();

		/*
		 * Export group.
		 */

		this.exportROIs = addFlag()
				.key( "EXPORT_ROIS" )
				.name( "Export ROIs" )
				.help( "If set, ROIs will be computed from the labels output and added to the input image." )
				.defaultValue( true )
				.get();

		this.exportLabels = addFlag()
				.key( "EXPORT_LABELS" )
				.name( "Export label image" )
				.help( "If set, the label image will be shown." )
				.defaultValue( false )
				.get();

		this.exportFlows = addFlag()
				.key( "EXPORT_FLOWS" )
				.name( "Export flows" )
				.help( "If set, the Cellpose flows will be shown as a 3-channel image" )
				.defaultValue( false )
				.get();

		addGroup( "Export options" )
				.add( exportROIs )
				.add( exportLabels )
				.add( exportFlows )
				.collapsed( false )
				.get();
	}
}
