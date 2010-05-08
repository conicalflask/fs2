package client.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;

import client.gui.TabItem.FS2Tab;

import common.FS2Constants;
import common.Util;

/**
 * This is the status bar for FS2. 
 * @author gary
 */
@SuppressWarnings("serial")
public class StatusBar extends JPanel implements ActionListener, MouseListener {
	
	JLabel infoText = new JLabel("Welcome to FS2...");
	JLabel upInfo;
	JLabel downInfo;
	JLabel chatInfo;
	MainFrame frame;
	Timer updater;
	
	public StatusBar(MainFrame frame) {
		super(new BorderLayout());
		
		this.frame = frame;
		
		infoText.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		add(infoText,BorderLayout.WEST);
		
		JPanel statsArea = new JPanel(new FlowLayout(FlowLayout.RIGHT,5,2));
		
		add(statsArea, BorderLayout.EAST);
		
		chatInfo = new JLabel("0", frame.gui.getUtil().getImage("chat"), JLabel.LEFT);
		chatInfo.addMouseListener(this);
		statsArea.add(chatInfo);
		
		upInfo = new JLabel("uploads info", frame.gui.util.getImage("upload"), JLabel.LEFT);
		upInfo.addMouseListener(this);
		statsArea.add(upInfo);
		downInfo = new JLabel("downloads info", frame.gui.util.getImage("download"), JLabel.LEFT);
		downInfo.addMouseListener(this);
		statsArea.add(downInfo);
		
		setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));
		
		updater = new Timer(FS2Constants.CLIENT_STATUS_BAR_UPDATE_INTERVAL, this);
		updater.start();
	}
	
	public void setInfoTextWithIcon(String info, Icon ico) {
		infoText.setText(info);
		infoText.setIcon(ico);
	}
	
	public void setInfoText(String info) {
		setInfoTextWithIcon(info, null);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==updater) {
			//1) speed labels:
			String upStr = /*Util.niceSize( frame.gui.ssvr.getUploadTracker().getPosition(), true)+"@"+*/ Util.niceSize((long) frame.gui.ssvr.getUploadTracker().getSpeed(),true)+"/s ["+Util.niceSize(frame.gui.ssvr.getUploadSpeed(), true)+"/s]";
			upInfo.setText(upStr);
			String downStr = /*Util.niceSize( frame.gui.dc.getDownloadTracker().getPosition(), true)+"@"+*/ Util.niceSize((long) frame.gui.dc.getDownloadTracker().getSpeed(),true)+"/s ["+Util.niceSize(frame.gui.dc.getDownloadSpeed(), true)+"/s]";
			downInfo.setText(downStr);
			
			//2) chat label:
			if (frame.getInstantiatedTabs().containsKey(FS2Tab.CHAT)) {
				chatInfo.setText( Integer.toString(((ChatTab)frame.getInstantiatedTabs().get(FS2Tab.CHAT)).getUnreadMessageCount()) );
			}
			
			frame.trayicon.setPopupInfoString("Up: "+upStr+"    Down: "+downStr);
		}
	}
	
	/**
	 * Shuts down the status bar, (stops the internal update timer)
	 */
	void shutdown() {
		updater.stop();
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		if (e.getSource()==chatInfo) {
			frame.openTab(FS2Tab.CHAT);
		} else if (e.getSource()==upInfo || e.getSource()==downInfo) {
			frame.openTab(FS2Tab.STATS);
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {}
	
}
