package com.ensoftcorp.open.pcg.factory;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.open.commons.analysis.CFG;
import com.ensoftcorp.open.pcg.common.PCG;

public class PCGFactory {
	
	/**
	 * Construct the PCG for the given CFG and the events of interest
	 * @param cfg
	 * @param method
	 * @param events
	 * @return PCG
	 */
	public static Q PCG(Q cfg, Q events){
		return new PCG(cfg.eval(), events.eval().nodes()).createPCG();
	}
	
	/**
	 * Construct the PCG for the given CFG and the events of interest
	 * @param cfg
	 * @param method
	 * @param events
	 * @return PCG
	 */
	public static Q PCG(Q cfg, Q cfRoots, Q events){
		return new PCG(cfg.eval(), cfRoots.eval().nodes(), events.eval().nodes()).createPCG();
	}
	
	/**
	 * Construct the PCG for the given a method and the events of interest
	 * @param method
	 * @param events
	 * @return PCG
	 */
	public static Q PCGforMethod(Q method, Q events){
//		Q cfg = CFG.excfg(method);
		Q cfg = CFG.cfg(method);
		return PCG(cfg, events);
	}
}
