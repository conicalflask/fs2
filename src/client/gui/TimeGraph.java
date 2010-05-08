package client.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.JPanel;

import common.Util;

/**
 * TimeGraph - a simple graphing class.  Every time a set of points is added to it,
 * it redraws.  Consequently, timing must be handled outside of this class.  If
 * points with negative values are passed, they will not be drawn.
 * @author Andy
 */
public class TimeGraph extends JPanel implements ComponentListener {

	private static final long serialVersionUID = 9073578486549895961L;
	private int viewHeight = 100;
	private int viewWidth = 100;
	
	private final static int numPoints = 100;	// Define how many points to remember
	
	private LinkedList<double[]> data = new LinkedList<double[]>();
	private int numTraces;
	private double max;
	
	static final int numTraceColors = 9;
	private Color[] traceColors = new Color[numTraceColors];	// Colours to use for the chart
	
	/**
	 * Constructor
	 * @param numTraces - number of traces to draw on the graph
	 */
	public TimeGraph(int numTraces) {
		if (numTraces > numTraceColors) {
			throw new IllegalArgumentException("Too many traces to graph!  Maximum is " + numTraceColors);
		}
		
		this.numTraces = numTraces;
		
		// Set up colours
		traceColors[0] = Color.GREEN;
		traceColors[1] = Color.RED;
		traceColors[2] = Color.YELLOW;
		traceColors[3] = Color.BLUE;
		traceColors[4] = Color.PINK;
		traceColors[5] = Color.ORANGE;
		traceColors[6] = Color.WHITE;
		traceColors[7] = Color.CYAN;
		traceColors[8] = Color.DARK_GRAY;
		
		this.addComponentListener(this);
	}
	
	/**
	 * Adds a set of points to the graph (graph is redrawn)
	 * @param points - array of data points
	 * @throws IllegalArgumentException if incorrect size of array is passed
	 */
	public void addPoint(double[] points) throws IllegalArgumentException {
		if (points.length != numTraces) {
			throw new IllegalArgumentException("Incorrect size of points array passed (passed: " + points.length + ")");
		}
		
		data.push(points);	// Put the new data on the front of the list (right)
		
		// Allow old data to 'fall off' the end of the list (left)
		while (data.size() > numPoints) {
			data.removeLast();
		}
		
		recalculateMax(true);		// Not at all efficient, but works
		
		this.repaint();
	}	
	
	private void recalculateSize() {
		Dimension size = this.getSize();
		viewHeight = size.height;
		viewWidth = size.width;
		
		this.repaint();
	}

	private void recalculateMax(boolean completeScan) {
		if (completeScan) {
			max = 0.0;
			for (double[] points : data) {
				for (int i = 0; i < points.length; i++) {
					if (points[i] > max) {
						max = points[i];
					}
				}
			}
		} else {
			double[] points = data.getFirst();

			for (int i = 0; i < points.length; i++) {
				if (points[i] > max) {
					max = points[i];
				}
			}
		}
	}
	
	/**
	 * Paints component
	 */
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
		
		// Calculate bar widths
		double barWidth = (double)viewWidth / (double)numPoints;
		double barScale = (double)viewHeight / max;
		
		int x = Math.round(viewWidth);
		int y = Math.round(viewHeight);
		int w = 0;
		int width = (int)Math.round(barWidth);
		
		// Create a polygon for each trace and give it a start point
		Polygon[] p = new Polygon[numTraces];
		for (int i = 0; i < p.length; i++) {
			p[i] = new Polygon();
			p[i].addPoint(viewWidth, viewHeight);
		}
		
		// Add the data points to the polygons
		for (double[] points : data) {
			for (int i = 0; i < points.length; i++) {
				p[i].addPoint(x+w, y-(int)Math.round(barScale * points[i]));
			}
			w -= width;
		}
		
		// Give an alpha gradient fill to each polygon 
		for (int i = 0; i < p.length; i++) {
			p[i].addPoint(x+w, viewHeight);
			Color color1 = new Color(traceColors[i].getRed(), traceColors[i].getGreen(), traceColors[i].getBlue(), 96);
			Color color2 = new Color(traceColors[i].getRed(), traceColors[i].getGreen(), traceColors[i].getBlue(), 64);
			GradientPaint grad = new GradientPaint(0,viewHeight/2,color1,0,viewHeight,color2);
			g.setPaint(grad);
			g.fillPolygon(p[i]);
		}
		
		// Give a solid colour trace for each polygon
		for (int i = 0; i < p.length; i++) {
			g.setColor(traceColors[i]);
			g.drawPolygon(p[i]);
		}
		
		// Finally give an idea of scale, with max and min markers
		g.setColor(Color.BLACK);
		
		// TODO: Don't have this hard-coded for bytes/sec - provide some kind of callback or similar
		FontMetrics fm = g.getFontMetrics();
		String s = Util.niceSize(Math.round(max))+"/s";
		g.drawString(s, viewWidth-fm.stringWidth(s), 10);
		s = Util.niceSize(Math.round(max/2))+"/s";
		g.drawString(s, viewWidth-fm.stringWidth(s), viewHeight/2);
	}	
	
	/**
	 * Returns preferred size
	 */
	public Dimension getPreferredSize () {
		return new Dimension (viewHeight, viewWidth);
	}
	
	// Component Listener methods
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
		recalculateSize();
	}
}
