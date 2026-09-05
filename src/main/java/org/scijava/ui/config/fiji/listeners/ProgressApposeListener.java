package org.scijava.ui.config.fiji.listeners;

import java.util.function.Consumer;

import org.apposed.appose.Builder.ProgressConsumer;
import org.apposed.appose.TaskEvent;
import org.scijava.ui.config.visitors.gui.FrameBuilder.ConfigFrame.Progress;

import net.imglib2.appose.util.ApposeTaskListener;

/**
 * Implementation of ApposeTaskListener that writes messages to a Progress
 * instance. Error messages are written to the standard error output.
 */
public class ProgressApposeListener implements ApposeTaskListener
{

	private final Progress progress;

	public ProgressApposeListener( final Progress progress )
	{
		this.progress = progress;
	}

	@Override
	public Consumer< TaskEvent > taskListener()
	{
		return event -> {
			if ( event.message != null && !event.message.isEmpty() )
				progress.message( event.responseType + " - " + event.message );
			if ( event.maximum > 0 )
				progress.set( ( double ) event.current / event.maximum );
		};
	}

	@Override
	public Consumer< String > outputListener()
	{
		return msg -> progress.message( msg );
	}

	@Override
	public Consumer< String > errorListener()
	{
		return s -> progress.message( "ERROR: " + s );
	}

	@Override
	public ProgressConsumer progressListener()
	{
		return ( t, c, m ) -> progress.set( ( double ) c / m, t );
	}

	@Override
	public void message( final String msg )
	{
		progress.message( msg );
	}
}
