package org.scijava.ui.config.fiji;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.event.WindowAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.scijava.ui.config.Configurator;
import org.scijava.ui.config.utils.GuiUtils;
import org.scijava.ui.config.visitors.Prefs;
import org.scijava.ui.config.visitors.gui.FrameBuilder;
import org.scijava.ui.config.visitors.gui.FrameBuilder.ConfigFrame;

import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;

/**
 * This is a version of {@link ConfigFijiPlugin} that manages and display what
 * is the input image.
 * <p>
 * It adds a panel at the top of the frame that shows the current active image.
 * The user can change the active image by clicking on another image window. The
 * currently selected image can be accessed via {@link #getImagePlus()}. If
 * there is not active image, this method returns <code>null</code>, so it is
 * the responsibility of concrete implementations to check for this case and
 * handle it accordingly.
 * 
 * @param <C>
 *            the type of the config class
 */
public abstract class ConfigFijiPluginFrame< C extends Configurator > extends ConfigFijiPlugin< C >
{

	private Supplier< ImagePlus > impSupplier;

	@Override
	protected void showUI()
	{
		Prefs.deserialize( getConfig() );
		final ImagePlus imp = getImagePlus();
		final ConfigFrame frame = FrameBuilder.build( getConfig(), this, createConfig( imp ) );
		this.impSupplier = decorate( frame );
		frame.pack();
		if ( imp != null && imp.getWindow() != null )
			GuiUtils.positionWindow( frame, imp.getWindow() );
		else
			frame.setLocationRelativeTo( null );
		frame.setTitle( frame.getTitle() );
		frame.setVisible( true );
	}

	@Override
	public ImagePlus getImagePlus()
	{
		return impSupplier != null ? impSupplier.get() : WindowManager.getCurrentImage();
	}

	private static Supplier< ImagePlus > decorate( final ConfigFrame frame )
	{
		final ImpPanel impPanel = new ImpPanel();
		frame.add( impPanel, BorderLayout.NORTH );
		frame.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosed( final java.awt.event.WindowEvent e )
			{
				impPanel.stopListening();
			}
		} );
		return () -> impPanel.imp;
	}

	public static class ImpPanel extends JPanel implements PropertyChangeListener, ImageListener
	{

		private static final long serialVersionUID = 1L;

		private final KeyboardFocusManager kfm;

		private final JLabel lbl;

		private ImagePlus imp;

		public ImpPanel()
		{
			// Listen to the active window being changed.
			kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
			kfm.addPropertyChangeListener( "activeWindow", this );
			// Listen to images being closed
			ImagePlus.addImageListener( this );

			setBorder( BorderFactory.createCompoundBorder(
					BorderFactory.createEmptyBorder( 5, 5, 5, 5 ),
					BorderFactory.createCompoundBorder(
							BorderFactory.createLineBorder( Color.LIGHT_GRAY ),
							BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) ) ) );
			setLayout( new BoxLayout( this, BoxLayout.X_AXIS ) );
			add( new JLabel( "Active image: " ) );
			add( Box.createHorizontalGlue() );
			this.lbl = new JLabel();
			lbl.setFont( lbl.getFont().deriveFont( Font.ITALIC ) );
			add( lbl );
			refresh( WindowManager.getCurrentImage() );
		}

		private void refresh( final ImagePlus imp )
		{
			this.imp = imp;
			lbl.setText( imp != null ? imp.getTitle() : "None" );
		}

		@Override
		public void propertyChange( final PropertyChangeEvent evt )
		{
			final Object newValue = evt.getNewValue();
			if ( newValue instanceof ImageWindow )
			{
				final ImageWindow win = ( ImageWindow ) newValue;
				final ImagePlus imp = win.getImagePlus();
				if ( imp != null )
					refresh( imp );
				return;
			}
			// Clear if there are no more images open.
			if ( WindowManager.getImageCount() == 0 )
				refresh( null );
		}

		public void stopListening()
		{
			if ( kfm != null )
				kfm.removePropertyChangeListener( "activeWindow", this );
		}

		@Override
		public void imageOpened( final ImagePlus imp )
		{}

		@Override
		public void imageClosed( final ImagePlus imp )
		{
			if ( imp == this.imp )
				refresh( null );
		}

		@Override
		public void imageUpdated( final ImagePlus imp )
		{}
	}
}
