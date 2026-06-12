package org.scijava.ui.config.fiji.visitors;

import java.util.HashMap;
import java.util.Map;

import org.scijava.ui.config.Configurator;
import org.scijava.ui.config.visitors.Maps;

public class IJMacro
{
	/**
	 * Fills the values of the specified Configurator from the provided ImageJ
	 * macro options string ("key1=val1 key2=val2 flag"). Important: only the
	 * values that are present in the macro options string will be set in the
	 * config, the rest will remain unchanged.
	 */
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static < C extends Configurator > void optionsToConfig( final String macroOptions, final C config )
	{
		// Used to get value classes.
		final Map< String, Object > defaultMap = Maps.toMap( config );

		// Used to store the parsed values, with classes that the config object expects.
		final Map< String, Object > targetMap = new HashMap<>();

		// Parse macro option string.
		final String[] tokens = macroOptions.split( " (?=(?:[^\\[\\]]*\\[[^\\[\\]]*\\])*[^\\[\\]]*$)" );
		for ( final String token : tokens )
		{
			if ( token.contains( "=" ) )
			{
				// Key and value in the macro options
				final String[] kv = token.split( "=", 2 );
				final String key = kv[ 0 ];
				String val = kv[ 1 ];
				if ( val.startsWith( "[" ) && val.endsWith( "]" ) )
					val = val.substring( 1, val.length() - 1 );

				if ( defaultMap.containsKey( key ) )
				{
					final Object defaultVal = defaultMap.get( key );
					if ( defaultVal instanceof String )
						targetMap.put( key, val );
					else if ( defaultVal instanceof Boolean )
						targetMap.put( key, Boolean.parseBoolean( val ) );
					else if ( defaultVal instanceof Double || defaultVal instanceof Float )
						targetMap.put( key, Double.parseDouble( val ) );
					else if ( defaultVal instanceof Integer )
						targetMap.put( key, Integer.parseInt( val ) );
					else if ( defaultVal instanceof Enum )
						targetMap.put( key, Enum.valueOf( ( Class< Enum > ) defaultVal.getClass(), val ) );
					else
						throw new IllegalArgumentException( "Unsupported parameter type for key: " + key );
				}
				else
				{
					throw new IllegalArgumentException( "Unknown option key: " + key );
				}
			}
			else
			{
				// Boolean flag: presence = true
				final String key = token;
				if ( defaultMap.containsKey( key ) && defaultMap.get( key ) instanceof Boolean )
					targetMap.put( key, Boolean.TRUE );
			}
		}

		// Now we put all the parsed values in the config object from the target
		// map.
		Maps.fromMap( targetMap, config );
	}
}
