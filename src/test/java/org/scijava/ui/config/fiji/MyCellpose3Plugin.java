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
