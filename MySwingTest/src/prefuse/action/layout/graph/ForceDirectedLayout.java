package prefuse.action.layout.graph;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Iterator;

import prefuse.action.layout.Layout;
import prefuse.data.Graph;
import prefuse.data.Schema;
import prefuse.data.tuple.TupleSet;
import prefuse.util.PrefuseLib;
import prefuse.util.force.DragForce;
import prefuse.util.force.ForceItem;
import prefuse.util.force.ForceSimulator;
import prefuse.util.force.NBodyForce;
import prefuse.util.force.SpringForce;
import prefuse.visual.EdgeItem;
import prefuse.visual.NodeItem;
import prefuse.visual.VisualItem;

/**
 * <p>
 * Layout that positions graph elements based on a physics simulation of
 * interacting forces; by default, nodes repel each other, edges act as springs,
 * and drag forces (similar to air resistance) are applied. This algorithm can
 * be run for multiple iterations for a run-once layout computation or
 * repeatedly run in an animated fashion for a dynamic and interactive layout.
 * </p>
 * 
 * <p>
 * The running time of this layout algorithm is the greater of O(N log N) and
 * O(E), where N is the number of nodes and E the number of edges. The addition
 * of custom force calculation modules may, however, increase this value.
 * </p>
 * 
 * <p>
 * The {@link prefuse.util.force.ForceSimulator} used to drive this layout can
 * be set explicitly, allowing any number of custom force directed layouts to be
 * created through the user's selection of included
 * {@link prefuse.util.force.Force} components. Each node in the layout is
 * mapped to a {@link prefuse.util.force.ForceItem} instance and each edge to a
 * {@link prefuse.util.force.Spring} instance for storing the state of the
 * simulation. See the {@link prefuse.util.force} package for more.
 * </p>
 * 
 * @author <a href="http://jheer.org">jeffrey heer</a>
 */
public class ForceDirectedLayout extends Layout {

	private ForceSimulator m_fsim;
	private long m_lasttime = -1L;
	private long m_maxstep = 50L;
	private boolean m_runonce;
	private int m_iterations = 100;
	private boolean m_enforceBounds;

	protected transient VisualItem referrer;

	protected String m_nodeGroup;
	protected String m_edgeGroup;

	/**
	 * Create a new ForceDirectedLayout. By default, this layout will not
	 * restrict the layout to the layout bounds and will assume it is being run
	 * in animated (rather than run-once) fashion.
	 * 
	 * @param graph
	 *            the data group to layout. Must resolve to a Graph instance.
	 */
	public ForceDirectedLayout(String graph) {
		this(graph, false, false);
	}

	/**
	 * Create a new ForceDirectedLayout. The layout will assume it is being run
	 * in animated (rather than run-once) fashion.
	 * 
	 * @param group
	 *            the data group to layout. Must resolve to a Graph instance.
	 * @param enforceBounds
	 *            indicates whether or not the layout should require that all
	 *            node placements stay within the layout bounds.
	 */
	public ForceDirectedLayout(String group, boolean enforceBounds) {
		this(group, enforceBounds, false);
	}

	/**
	 * Create a new ForceDirectedLayout.
	 * 
	 * @param group
	 *            the data group to layout. Must resolve to a Graph instance.
	 * @param enforceBounds
	 *            indicates whether or not the layout should require that all
	 *            node placements stay within the layout bounds.
	 * @param runonce
	 *            indicates if the layout will be run in a run-once or animated
	 *            fashion. In run-once mode, the layout will run for a set
	 *            number of iterations when invoked. In animation mode, only one
	 *            iteration of the layout is computed.
	 */
	public ForceDirectedLayout(String group, boolean enforceBounds, boolean runonce) {
		super(group);
		m_nodeGroup = PrefuseLib.getGroupName(group, Graph.NODES);
		m_edgeGroup = PrefuseLib.getGroupName(group, Graph.EDGES);

		m_enforceBounds = enforceBounds;
		m_runonce = runonce;
		m_fsim = new ForceSimulator();
		m_fsim.addForce(new NBodyForce());
		m_fsim.addForce(new SpringForce());
		m_fsim.addForce(new DragForce());
	}

	/**
	 创建一个新的forcedirectedlayout。该布局将假设它是运行在动画（而不是运行一次）时尚。
           参数:
                 组数据组布局。必须解析为一个图形实例。
        FSIM力模拟器用于驱动布置计算
        enforcebounds指示是否布局应当要求所有节点安置在规划范围。
	 */
	public ForceDirectedLayout(String group, ForceSimulator fsim, boolean enforceBounds) {
		this(group, fsim, enforceBounds, false);
	}

	/**
	 * Create a new ForceDirectedLayout.
	 * 
	 * @param group
	 *            the data group to layout. Must resolve to a Graph instance.
	 * @param fsim
	 *            the force simulator used to drive the layout computation
	 *            用于驱动布局计算的力模拟器
	 * @param enforceBounds
	 *            indicates whether or not the layout should require that all
	 *            node placements stay within the layout bounds. 指示是否布局应该要求所有的节点位置保持在布局范围内
	 *            
	 * @param runonce
	 *            indicates if the layout will be run in a run-once or animated
	 *            fashion. In run-once mode, the layout will run for a set
	 *            number of iterations when invoked. In animation mode, only one
	 *            iteration of the layout is computed.指示如果布局将在运行一次或动画的方式运行。在运行一次模式时，
	 *            将在调用时运行一个设置的迭代次数。在动画模式中，只有一个迭代的布局计算
	 */
	public ForceDirectedLayout(String group, ForceSimulator fsim, boolean enforceBounds, boolean runonce) {
		super(group);
		m_nodeGroup = PrefuseLib.getGroupName(group, Graph.NODES);
		m_edgeGroup = PrefuseLib.getGroupName(group, Graph.EDGES);
		m_enforceBounds = enforceBounds;
		m_runonce = runonce;
		m_fsim = fsim;
	}

	// ------------------------------------------------------------------------

	/**
	 * Get the maximum timestep allowed for integrating node settings between
	 * runs of this layout. When computation times are longer than desired, and
	 * node positions are changing dramatically between animated frames, the max
	 * step time can be lowered to suppress node movement.
	 * 得到允许将这种布局之间运行节点设置的最大时间步长。当计算时间长于所需的时间，
	 * 和节点位置之间的动画帧之间的显着变化，最大步长时间可以降低，以抑制节点的运动。
	 * 
	 * @return the maximum timestep allowed for integrating between two layout
	 *         steps.
	 */
	public long getMaxTimeStep() {
		return m_maxstep;
	}

	/**
	 * Set the maximum timestep allowed for integrating node settings between
	 * runs of this layout. When computation times are longer than desired, and
	 * node positions are changing dramatically between animated frames, the max
	 * step time can be lowered to suppress node movement.
	 * 设置允许整合这种布局之间运行节点设置的最大时间步长。
	 * 当计算时间长于所需的时间，
	 * 和节点位置之间的动画帧之间的显着变化，最大步长时间可以降低，以抑制节点的运动。
	 * 
	 * @param maxstep
	 *            the maximum timestep allowed for integrating between two
	 *            layout steps
	 */
	public void setMaxTimeStep(long maxstep) {
		this.m_maxstep = maxstep;
	}

	/**
	 * Get the force simulator driving this layout.获取驱动此布局的力模拟器。
	 * 
	 * @return the force simulator
	 */
	public ForceSimulator getForceSimulator() {
		return m_fsim;
	}

	/**
	 * Set the force simulator driving this layout.驱动此布局的力模拟器
	 * 
	 * @param fsim
	 *            the force simulator
	 */
	public void setForceSimulator(ForceSimulator fsim) {
		m_fsim = fsim;
	}

	/**
	 * Get the number of iterations to use when computing a layout in run-once
	 * mode.获取在运行一次模式下的布局时使用的迭代次数
	 * 
	 * @return the number of layout iterations to run
	 */
	public int getIterations() {
		return m_iterations;
	}

	/**
	 * Set the number of iterations to use when computing a layout in run-once
	 * mode.运行一次模式下的布局时使用的迭代次数
	 * 
	 * @param iter
	 *            the number of layout iterations to run
	 */
	public void setIterations(int iter) {
		if (iter < 1)
			throw new IllegalArgumentException("Iterations must be a positive number!");
		m_iterations = iter;
	}

	/**
	 * Explicitly sets the node and edge groups to use for this layout,
	 * overriding the group setting passed to the constructor.
	 * 显式设置此布局的节点和边缘组使用此布局，重写传递给构造函数的组设置
	 * 
	 * @param nodeGroup
	 *            the node data group
	 * @param edgeGroup
	 *            the edge data group
	 */
	public void setDataGroups(String nodeGroup, String edgeGroup) {
		m_nodeGroup = nodeGroup;
		m_edgeGroup = edgeGroup;
	}

	// ------------------------------------------------------------------------

	/**
	 * @see prefuse.action.Action#run(double)
	 */
	public void run(double frac) {
		// perform different actions if this is a run-once or
		// run-continuously layout
		if (m_runonce) {
			Point2D anchor = getLayoutAnchor();//得到根节点
			Iterator iter = m_vis.visibleItems(m_nodeGroup);
			while (iter.hasNext()) {
				VisualItem item = (NodeItem) iter.next();
				item.setX(anchor.getX());
				item.setY(anchor.getY());   //所有节点坐标都是中心点坐标
			}
			m_fsim.clear();
			long timestep = 1000L;
			initSimulator(m_fsim);                 //初始化模拟器    映射图形与模拟器
			for (int i = 0; i < m_iterations; i++) {
				// use an annealing schedule to set time step      使用模拟退火程序设定时间步长
				timestep *= (1.0 - i / (double) m_iterations);
				long step = timestep + 50;
				// run simulator
				m_fsim.runSimulator(step);             //运行模拟器
				// debugging output
				// if (i % 10 == 0 ) {
				// System.out.println("iter: "+i);
				// }
			}
			updateNodePositions();          //更新节点位置
		} else {
			try {
				// get timestep
				if (m_lasttime == -1)
					m_lasttime = System.currentTimeMillis() - 20;
				long time = System.currentTimeMillis();
				long timestep = Math.min(m_maxstep, time - m_lasttime);
				m_lasttime = time;
				// run force simulator
				m_fsim.clear();
				initSimulator(m_fsim);
				m_fsim.runSimulator(timestep);
				updateNodePositions();

			} catch(NullPointerException e){
			}catch (Exception e) {
			}
		}
		if (frac == 1.0) {
			reset();
		}
	}

	private void updateNodePositions() {//更新节点的位置
		Rectangle2D bounds = getLayoutBounds();
		double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
		if (bounds != null) {
			x1 = bounds.getMinX();
			y1 = bounds.getMinY();
			x2 = bounds.getMaxX();
			y2 = bounds.getMaxY();
		}

		// update positions
		Iterator iter = m_vis.visibleItems(m_nodeGroup);
		while (iter.hasNext()) {
			VisualItem item = (VisualItem) iter.next();
			ForceItem fitem = (ForceItem) item.get(FORCEITEM);

			if (item.isFixed()) {//如果某个item固定，则对该item不作计算
				// clear any force computations
				fitem.force[0] = 0.0f;
				fitem.force[1] = 0.0f;
				fitem.velocity[0] = 0.0f;
				fitem.velocity[1] = 0.0f;

				if (Double.isNaN(item.getX())) {
					setX(item, referrer, 0.0);
					setY(item, referrer, 0.0);
				}
				continue;
			}

			double x = fitem.location[0];
			double y = fitem.location[1];

			if (m_enforceBounds && bounds != null) {
				Rectangle2D b = item.getBounds();
				double hw = b.getWidth() / 2;
				double hh = b.getHeight() / 2;
				if (x + hw > x2)
					x = x2 - hw;
				if (x - hw < x1)
					x = x1 + hw;
				if (y + hh > y2)
					y = y2 - hh;
				if (y - hh < y1)
					y = y1 + hh;
			}

			// set the actual position
			setX(item, referrer, x);
			setY(item, referrer, y);
		}
	}

	/**
	 * Reset the force simulation state for all nodes processed by this layout.
	 */
	public void reset() {
		Iterator iter = m_vis.visibleItems(m_nodeGroup);
		while (iter.hasNext()) {
			VisualItem item = (VisualItem) iter.next();
			ForceItem fitem = (ForceItem) item.get(FORCEITEM);
			if (fitem != null) {
				fitem.location[0] = (float) item.getEndX();
				fitem.location[1] = (float) item.getEndY();
				fitem.force[0] = fitem.force[1] = 0;
				fitem.velocity[0] = fitem.velocity[1] = 0;
			}
		}
		m_lasttime = -1L;
	}
	/**
	 * Loads the simulator with all relevant force items and springs.
	 * 用所有相关的力项和弹簧加载模拟器。
	 * 
	 * @param fsim
	 *            the force simulator driving this layout
	 */
	protected void initSimulator(ForceSimulator fsim) {
		// make sure we have force items to work with
		TupleSet ts = m_vis.getGroup(m_nodeGroup);
		if (ts == null)
			return;
		try {
			ts.addColumns(FORCEITEM_SCHEMA);
		} catch (IllegalArgumentException iae) { /* ignored */
		}

		float startX = (referrer == null ? 0f : (float) referrer.getX());
		float startY = (referrer == null ? 0f : (float) referrer.getY());
		startX = Float.isNaN(startX) ? 0f : startX;
		startY = Float.isNaN(startY) ? 0f : startY;

		Iterator iter = m_vis.visibleItems(m_nodeGroup);
		while (iter.hasNext()) {
			VisualItem item = (VisualItem) iter.next();
			ForceItem fitem = (ForceItem) item.get(FORCEITEM);
			fitem.mass = getMassValue(item);
			double x = item.getEndX();
			double y = item.getEndY();
			fitem.location[0] = (Double.isNaN(x) ? startX : (float) x);
			fitem.location[1] = (Double.isNaN(y) ? startY : (float) y);
			//			System.out.println("item:" + item.toString() + "\tx:" + fitem.location[0] + "\ty:" + fitem.location[1]);
			fsim.addItem(fitem);             //依次将节点添加到模拟器fsim中
		}
		if (m_edgeGroup != null) {
			iter = m_vis.visibleItems(m_edgeGroup);
			while (iter.hasNext()) {
				EdgeItem e = (EdgeItem) iter.next();
				NodeItem n1 = e.getSourceItem();
				ForceItem f1 = (ForceItem) n1.get(FORCEITEM);
				NodeItem n2 = e.getTargetItem();
				ForceItem f2 = (ForceItem) n2.get(FORCEITEM);
				//System.out.println(n1.get("device_na")+"孩子节点个数"+n2.getChildCount());
				float coeff = getSpringCoefficient(e);
				float slen = getSpringLength(e);
				fsim.addSpring(f1, f2, (coeff >= 0 ? coeff : -1.f), (slen >= 0 ? slen : -1.f));//添加节点之间的弹簧

			}
		}
	}

	/**
	 * Get the mass value associated with the given node. Subclasses should
	 * override this method to perform custom mass assignment.
	 * 获取与给定节点关联的质量值。子类应重写此方法来执行自定义的质量分配
	 * @param n
	 *            the node for which to compute the mass value
	 * @return the mass value for the node. By default, all items are given a
	 *         mass value of 1.0.
	 */
	protected float getMassValue(VisualItem n) {
		n.setEndSize(100);
		return 1.0f;
	}

	/**
	 * Get the spring length for the given edge. Subclasses should override this
	 * method to perform custom spring length assignment.
	 * 获取给定边缘的弹簧长度。子类应重写此方法来执行自定义的弹簧长度分配
	 * @param e
	 *            the edge for which to compute the spring length
	 * @return the spring length for the edge. A return value of -1 means to
	 *         ignore this method and use the global default.
	 */
	protected float getSpringLength(EdgeItem e) {
		return -1.f;
	}

	/**
	 * Get the spring coefficient for the given edge, which controls the tension
	 * or strength of the spring. Subclasses should override this method to
	 * perform custom spring tension assignment.
	 * 获得给定边缘的弹簧系数，从而控制弹簧的张力或强度。子类应重写此方法来执行自定义的弹簧张力分配。
	 * @param e
	 *            the edge for which to compute the spring coefficient.
	 * @return the spring coefficient for the edge. A return value of -1 means
	 *         to ignore this method and use the global default.
	 */
	protected float getSpringCoefficient(EdgeItem e) {
		// System.out.println(""+e.getTargetNode().getLastChild().getChildCount()); 
		//e.setSize(e.getSize()*2);
		return -1.f;
	}

	/**
	 * Get the referrer item to use to set x or y coordinates that are
	 * initialized to NaN.
	 * 
	 * @return the referrer item.
	 * @see prefuse.util.PrefuseLib#setX(VisualItem, VisualItem, double)
	 * @see prefuse.util.PrefuseLib#setY(VisualItem, VisualItem, double)
	 */
	public VisualItem getReferrer() {
		return referrer;
	}

	/**
	 * Set the referrer item to use to set x or y coordinates that are
	 * initialized to NaN.
	 * 设置引荐项目设置使用X或Y坐标初始化为NaN。
	 * 
	 * @param referrer
	 *            the referrer item to use.
	 * @see prefuse.util.PrefuseLib#setX(VisualItem, VisualItem, double)
	 * @see prefuse.util.PrefuseLib#setY(VisualItem, VisualItem, double)
	 */
	public void setReferrer(VisualItem referrer) {
		
		this.referrer = referrer;
	}

	// ------------------------------------------------------------------------
	// ForceItem Schema Addition

	/**
	 * The data field in which the parameters used by this layout are stored.
	 */
	public static final String FORCEITEM = "_forceItem";
	/**
	 * The schema for the parameters used by this layout.
	 */
	public static final Schema FORCEITEM_SCHEMA = new Schema();
	static {
		FORCEITEM_SCHEMA.addColumn(FORCEITEM, ForceItem.class, new ForceItem());
	}

} // end of class ForceDirectedLayout
