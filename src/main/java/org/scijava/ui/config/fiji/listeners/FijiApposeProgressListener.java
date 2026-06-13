package org.scijava.ui.config.fiji.listeners;

import org.scijava.ui.config.listeners.ApposeTaskListener.ProgressApposeListener;
import org.scijava.ui.config.visitors.gui.FrameBuilder.ConfigFrame.Progress;

import ij.IJ;

/**
 * An implementation of {@link ProgressApposeListener} that writes messages to a
 * {@link Progress} instance and shows error messages in a IJ error dialog.
 */
public class FijiApposeProgressListener extends ProgressApposeListener
{

	private String title;

	public FijiApposeProgressListener( final Progress progress, final String title )
	{
		super( progress );
		this.title = title;
	}

	@Override
	public void error( final String msg )
	{
		IJ.error( title, msg );
	}
}
