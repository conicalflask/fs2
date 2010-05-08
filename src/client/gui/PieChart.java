package client.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JPanel;

/**
 * A pie chart by Andy Raines, modded by Gary.
 * @author gary
 *
 */
public class PieChart extends JPanel implements ComponentListener {

	private static final long serialVersionUID = 1022660761851952757L;
	private int pieViewSize = 100;
	private Map<String, Double> wedges = new LinkedHashMap<String, Double>();
	private int pieDiameter;
	private int pieRadius;
	private int pieCenterPos;
	private double total = 0.0;

	static final int pieBorderWidth = 10; 				// pixels from pie edge to side
	static final Color shadowColor = Color.LIGHT_GRAY;	// colour for the pie shadow
	static final int shadowOffset = 3;					// no pixels to offset shadow by
	static final Color pieColor = Color.WHITE;			// colour to use for pie circle
	static final int minLabeledWedgeSize = 3;			// min size of a wedge for labelling, degrees.
	static final Color labelColor = Color.BLACK;		// colour to use for labels
	
	static final int numWedgeColors = 9;
	private Color[] wedgeColors = new Color[numWedgeColors];	// Colours to use for the chart

	public PieChart() {
		recalculateSize();
		super.addComponentListener(this);
	
		// Set up colours
		wedgeColors[0] = new Color(228,26,28);
		wedgeColors[1] = new Color(55,126,184);
		wedgeColors[2] = new Color(77,175,74);
		wedgeColors[3] = new Color(152,78,163);
		wedgeColors[4] = new Color(255,127,0);
		wedgeColors[5] = new Color(255,255,51);
		wedgeColors[6] = new Color(166,86,40);
		wedgeColors[7] = new Color(247,129,191);
		wedgeColors[8] = new Color(153,153,153);
	}
	
	public void addWedge(String label, double value) {
		wedges.put(label, value);
		recalculateTotal();
	}
	
	public void updateWedge(String label, double value) {
		addWedge(label, value);
	}
	
	public void removeWedge(String label) {
		wedges.remove(label);
		recalculateTotal();
	}
	
	public void removeAllWedges() {
		wedges.clear();
	}
	
	/**
	 * Changes the actual backing store for the wedges and updates.
	 * The backing store should be mutable if any of the other wedge operations are used, and should be ordered if this is important to presentation.
	 * @param newWedges
	 */
	public void setWedges(Map<String, Double> newWedges) {
		wedges = newWedges;
		recalculateTotal();
	}
	
	private void recalculateTotal() {
		total = 0.0;
		
		for (Double d : wedges.values()) total+=d;
		
		this.repaint();
	}
	
	private void recalculateSize() {
		Dimension size = this.getSize();

		pieViewSize = Math.min(size.height, size.width);
		
		pieDiameter = pieViewSize - 2*pieBorderWidth;
		pieRadius = pieDiameter/2;
		pieCenterPos = pieBorderWidth + pieRadius;
		this.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
	}
	
	public void paintComponent(Graphics g2) {
		super.paintComponent(g2);
		
		// Set up rendering hints
		Graphics2D g = (Graphics2D)g2;
		HashMap<RenderingHints.Key, Object> hints = new HashMap<RenderingHints.Key,Object>();
		hints.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		RenderingHints rh = new RenderingHints(hints);
		g.setRenderingHints(rh);

		// Draw shadow first
		g.setColor(shadowColor);
		g.fillOval(pieBorderWidth+shadowOffset, pieBorderWidth+shadowOffset, pieDiameter, pieDiameter);

		// Draw "blank" pie circle
		g.setColor(pieColor);
		g.fillOval(pieBorderWidth, pieBorderWidth, pieDiameter, pieDiameter);

		/* NB: Don't be tempted to merge the next two loops, the wedges
		 * must be drawn fully first before drawing the text to avoid
		 * nasty rendering issues. */

		// Initialise looping variables
		int arcPosition;
		int i;
		Iterator<String> wedgesIterator;
		
		// Draw wedges
		arcPosition = 0;
		i = 0;
		wedgesIterator = wedges.keySet().iterator();
		while (wedgesIterator.hasNext()) {
			String label = wedgesIterator.next();
			double value = wedges.get(label);
			int arcLength = (int)Math.round((value * 360) / total);
			arcPosition += arcLength;

			// If last wedge, then ensure pie is complete
			if (!wedgesIterator.hasNext() && arcPosition != 360) {
				arcLength += (360 - arcPosition);
				arcPosition += (360 - arcPosition);
			}
			
			if (i >= numWedgeColors) {
				while (i >= numWedgeColors) {
					i -= numWedgeColors;
				}
				i++;			// Ensure there are no collisions
			}

			g.setColor(wedgeColors[i]);	// Set wedge colour
			g.fillArc(pieBorderWidth, pieBorderWidth, pieDiameter, pieDiameter, (arcPosition - arcLength), arcLength);
			
			i++;
		}

		// Draw labels
		arcPosition = 0;
		wedgesIterator = wedges.keySet().iterator();
		while (wedgesIterator.hasNext()) {
			String label = wedgesIterator.next();
			double value = wedges.get(label);
			int arcLength = (int)Math.round((value * 360) / total);
			arcPosition += arcLength;

			// Ensure wedge is large enough to be labelled
			if (arcLength > minLabeledWedgeSize) {
				g.setColor(labelColor);

				// Work out the label position
				double angle = ((double)arcPosition - ((double)arcLength)/2) * Math.PI / 180.0;
				int x = pieCenterPos + (int)((pieRadius/1.3)*Math.cos(angle));
				int y = pieCenterPos - (int)((pieRadius/1.3)*Math.sin(angle)) + 5;
				g.drawString(label, x, y);
			}
		}
	}	
	
	@Override
	public void componentHidden(ComponentEvent e) {
	}

	@Override
	public void componentMoved(ComponentEvent e) {
	}

	@Override
	public void componentResized(ComponentEvent e) {
		// Find out how big we need to be
		recalculateSize();
	}

	@Override
	public void componentShown(ComponentEvent e) {
	}
}

