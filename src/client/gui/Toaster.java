/**
 * Java Toaster is a java utility class for your swing applications
 * that show an animate box coming from the bottom of your screen
 * with a notification message and/or an associated image 
 * (like msn online/offline notifications).
 * 
 * Toaster panel in windows system follow the taskbar; So if
 * the taskbar is into the bottom the panel coming from the bottom
 * and if the taskbar is on the top then the panel coming from the top.
 * 
 * This is a simple example of utilization:
 * 
 * import com.nitido.utils.toaster.*; 
 * import javax.swing.*; 
 * 
 * public class ToasterTest 
 * { 
 *
 *  public static void main(String[] args) 
 *  { 
 *   // Initialize toaster manager... 
 *   Toaster toasterManager = new Toaster(); 
 *
 *   // Show a simple toaster 
 *   toasterManager.showToaster( new ImageIcon( "mylogo.gif" ), "A simple toaster with an image" ); 
 *  } 
 * } 
 */
package client.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.border.TitledBorder;

import client.gui.TabItem.FS2Tab;
import client.gui.chat.NodeChatTab;

/**
 * Class to show toasters in multi-platform
 * 
 * @author Daniele Piras
 * 
 */
public class Toaster {
	// Width of the toster
	private int toasterWidth = 300;

	// Height of the toster
	private int toasterHeight = 80;

	// Step for the toaster
	private int step = 20;

	// Step time
	private int stepTime = 20;

	// Show time
	private int displayTime = 5000;

	// Current number of toaster...
	private int currentNumberOfToaster = 0;

	// Last opened toaster
	private int maxToaster = 0;

	// Max number of toasters for the sceen
	private int maxToasterInSceen;

	// Font used to display message
	private Font font;

	// Color for border
	private Color borderColor;

	// Color for toaster
	private Color toasterColor;

	// Set message color
	private Color messageColor;

	// Set the margin
	int margin;
	
	// Flag that indicate if use alwaysOnTop or not.
	// method always on top start only SINCE JDK 5 !
	boolean useAlwaysOnTop = true;

	// FS2 specific properties
	MainFrame frame;
	NodeChatTab chatTab;
	
	
	public static void main(String[] args){
		new Toaster(null,null).showToaster(null, "Dr. n00b's private reserve", "You have 10 new messages.");
	}
	
	
	/**
	 * Constructor to initialized toaster component...
	 * 
	 * @author daniele piras
	 * 
	 */
	public Toaster(MainFrame frame, NodeChatTab chatTab) {
		this.frame = frame;
		this.chatTab = chatTab;
		
		// Set default font...
		font = new Font("Arial", Font.PLAIN, 12);
		
		// Border color
		borderColor = new Color(132, 112, 255);
		toasterColor = Color.WHITE;
		messageColor = Color.BLACK;
		useAlwaysOnTop = true;
		
		// Verify AlwaysOnTop Flag...
		try {
			JWindow.class.getMethod("setAlwaysOnTop", new Class[] { Boolean.class });
		} catch (Exception e) {
			useAlwaysOnTop = false;
		}

		singleToaster = new SingleToaster();
	}

	/**
	 * Class that rappresent a single toaster
	 * 
	 * @author daniele piras
	 * 
	 */
	class SingleToaster extends javax.swing.JWindow {
		private static final long serialVersionUID = 1L;

		// Label to store Icon
		private JLabel iconLabel = new JLabel();

		// Text area for the message
		private JTextArea message = new JTextArea();
		
		// Label for the IndexNode name
		private JLabel indexNodeName = new JLabel();
		
		/***
		 * Simple costructor that initialized components...
		 */
		public SingleToaster() {
			initComponents();
		}

		/***
		 * Function to initialized components
		 */
		private void initComponents() {

			setSize(toasterWidth, toasterHeight);
			message.setFont(getToasterMessageFont());
			
			JPanel innerPanel = new JPanel(new BorderLayout(getMargin(),
					getMargin()));
			innerPanel.setBorder(new TitledBorder("FS2"));
			innerPanel.setForeground(frame.getForeground());
			innerPanel.setBackground(frame.getBackground());//getToasterColor());

			indexNodeName.setFont(new Font("monospaced", Font.BOLD, 14));
			
			message.setForeground(getMessageColor());
			message.setBackground(null);//getToasterColor());
			message.setMargin(new Insets(2, 2, 2, 2));
			message.setLineWrap(true);
			message.setWrapStyleWord(true);
			message.addMouseListener(new MouseListener() {
				
				@Override
				public void mouseReleased(MouseEvent e) { }
				
				@Override
				public void mousePressed(MouseEvent e) { }
				
				@Override
				public void mouseExited(MouseEvent e) { }
				
				@Override
				public void mouseEntered(MouseEvent e) { }
				
				@Override
				public void mouseClicked(MouseEvent e) { 
					frame.setVisible(true);
					frame.openTab(FS2Tab.CHAT);
					chatTab.getOwner().activateChatTab(chatTab);
				}
			});

			innerPanel.add(iconLabel, BorderLayout.EAST);
			innerPanel.add(indexNodeName, BorderLayout.NORTH);
			innerPanel.add(message, BorderLayout.CENTER);
			getContentPane().add(innerPanel);
		}

		/***
		 * Start toaster animation...
		 */
		public void animate() {
			(new Animation(this)).start();
		}

	}
	
	Boolean animating = false;

	/***
	 * Class that manage the animation
	 */
	class Animation extends Thread {
		SingleToaster toaster;

		public Animation(SingleToaster toaster) {
			this.toaster = toaster;
		}

		/**
		 * Animate vertically the toaster. The toaster could be moved from
		 * bottom to upper or to upper to bottom
		 * 
		 * @param posx
		 * @param fromy
		 * @param toy
		 * @throws InterruptedException
		 */
		protected void animateVertically(int posx, int fromY, int toY)
				throws InterruptedException {

			toaster.setLocation(posx, fromY);
			if (toY < fromY) {
				for (int i = fromY; i > toY; i -= step) {
					toaster.setLocation(posx, i);
					Thread.sleep(stepTime);
				}
			} else {
				for (int i = fromY; i < toY; i += step) {
					toaster.setLocation(posx, i);
					Thread.sleep(stepTime);
				}
			}
			toaster.setLocation(posx, toY);
		}


		public void run() {
			try {
				boolean animateFromBottom = true;
				GraphicsEnvironment ge = GraphicsEnvironment
						.getLocalGraphicsEnvironment();
				Rectangle screenRect = ge.getMaximumWindowBounds();
				

				int screenHeight = (int) screenRect.height;

				int startYPosition;
				int stopYPosition;

				if (screenRect.y > 0) {
					animateFromBottom = false; // Animate from top!
				}

				maxToasterInSceen = screenHeight / toasterHeight;

				int posx = (int) screenRect.width - toasterWidth - 1;

				toaster.setLocation(posx, screenHeight);
				toaster.setVisible(true);
				if (useAlwaysOnTop) {
					toaster.setAlwaysOnTop(true);
				}

				if (animateFromBottom) {
					startYPosition = screenHeight;
					stopYPosition = startYPosition - toasterHeight - 1;
					if (currentNumberOfToaster > 0) {
						stopYPosition = stopYPosition
								- (maxToaster % maxToasterInSceen * toasterHeight);
					} else {
						maxToaster = 0;
					}
				} else {
					startYPosition = screenRect.y - toasterHeight;
					stopYPosition = screenRect.y;

					if (currentNumberOfToaster > 0) {
						stopYPosition = stopYPosition
								+ (maxToaster % maxToasterInSceen * toasterHeight);
					} else {
						maxToaster = 0;
					}
				}

				currentNumberOfToaster++;
				maxToaster++;

				animating = true;
				animateVertically(posx, startYPosition, stopYPosition);
				Thread.sleep(displayTime);
				animateVertically(posx, stopYPosition, startYPosition);
				animating = false;

				currentNumberOfToaster--;
				toaster.setVisible(false);
				toaster.dispose();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	SingleToaster singleToaster;

	/**
	 * Show a toaster with the specified message and the associated icon.
	 */
	public void showToaster(Icon icon, String indexNodeName, String msg) {
		// singleToaster = new SingleToaster();
		if (icon != null) {
			singleToaster.iconLabel.setIcon(icon);
		}
		singleToaster.indexNodeName.setText(indexNodeName);
		singleToaster.message.setText(msg);
		if (!animating) singleToaster.animate();
	}

	/**
	 * Show a toaster with the specified message.
	 */
	public void showToaster(String msg) {
		showToaster(null, "", msg);
	}

	/**
	 * @return Returns the font
	 */
	public Font getToasterMessageFont() {
		return font;
	}

	/**
	 * Set the font for the message
	 */
	public void setToasterMessageFont(Font f) {
		font = f;
	}

	/**
	 * @return Returns the borderColor.
	 */
	public Color getBorderColor() {
		return borderColor;
	}

	/**
	 * @param borderColor
	 *            The borderColor to set.
	 */
	public void setBorderColor(Color borderColor) {
		this.borderColor = borderColor;
	}

	/**
	 * @return Returns the displayTime.
	 */
	public int getDisplayTime() {
		return displayTime;
	}

	/**
	 * @param displayTime
	 *            The displayTime to set.
	 */
	public void setDisplayTime(int displayTime) {
		this.displayTime = displayTime;
	}

	/**
	 * @return Returns the margin.
	 */
	public int getMargin() {
		return margin;
	}

	/**
	 * @param margin
	 *            The margin to set.
	 */
	public void setMargin(int margin) {
		this.margin = margin;
	}

	/**
	 * @return Returns the messageColor.
	 */
	public Color getMessageColor() {
		return messageColor;
	}

	/**
	 * @param messageColor
	 *            The messageColor to set.
	 */
	public void setMessageColor(Color messageColor) {
		this.messageColor = messageColor;
	}

	/**
	 * @return Returns the step.
	 */
	public int getStep() {
		return step;
	}

	/**
	 * @param step
	 *            The step to set.
	 */
	public void setStep(int step) {
		this.step = step;
	}

	/**
	 * @return Returns the stepTime.
	 */
	public int getStepTime() {
		return stepTime;
	}

	/**
	 * @param stepTime
	 *            The stepTime to set.
	 */
	public void setStepTime(int stepTime) {
		this.stepTime = stepTime;
	}

	/**
	 * @return Returns the toasterColor.
	 */
	public Color getToasterColor() {
		return toasterColor;
	}

	/**
	 * @param toasterColor
	 *            The toasterColor to set.
	 */
	public void setToasterColor(Color toasterColor) {
		this.toasterColor = toasterColor;
	}

	/**
	 * @return Returns the toasterHeight.
	 */
	public int getToasterHeight() {
		return toasterHeight;
	}

	/**
	 * @param toasterHeight
	 *            The toasterHeight to set.
	 */
	public void setToasterHeight(int toasterHeight) {
		this.toasterHeight = toasterHeight;
	}

	/**
	 * @return Returns the toasterWidth.
	 */
	public int getToasterWidth() {
		return toasterWidth;
	}

	/**
	 * @param toasterWidth
	 *            The toasterWidth to set.
	 */
	public void setToasterWidth(int toasterWidth) {
		this.toasterWidth = toasterWidth;
	}

}
