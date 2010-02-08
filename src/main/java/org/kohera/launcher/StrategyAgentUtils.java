package org.kohera.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.log4j.Logger;
import org.marketcetera.module.ModuleManager;
import org.marketcetera.module.ModuleManagerMXBean;
import org.marketcetera.module.ModuleURN;
import org.marketcetera.module.SinkModuleFactory;
import org.marketcetera.strategy.Language;
import org.marketcetera.strategy.StrategyModuleFactory;
import org.marketcetera.strategyagent.StrategyAgent;


public class StrategyAgentUtils {

	/* logging */
	private static final Logger logger =
		Logger.getLogger(Launcher.class);

	public static ModuleURN DESTINATION_SINK = 
		SinkModuleFactory.INSTANCE_URN;

	private static final char COMMA = ',';


	/**
	 * Start a strategy agent with the given list of commands.
	 * 
	 * @param inCommands
	 * @return
	 * @throws Exception 
	 * @throws Exception
	 */
	public static StrategyAgent startAgent(String... inCommands) throws Exception {

		/* create the commands file */
		File tmp = createCmdsFile(inCommands);

		/* run the strategy agent
		 * 
		 * this is total hackery because there is no programmatic
		 * way (other than reflection) to configure, initialize,
		 * and run a StrategyAgent (muppets)
		 */
		StrategyAgent agent = new StrategyAgentProxy();
		Method run = null;
		try {
			run = StrategyAgent.class.getDeclaredMethod(
					"run", StrategyAgent.class, String[].class);
			run.setAccessible(true); 						
			run.invoke(null, agent, new String[]{tmp.getAbsolutePath()});
		} catch (Exception e ) {
			throw new Exception("Could not run the StrategyAgent.");
		}

		/* delete the commands file */
		tmp.delete();
		return agent;
	}

	/**
	 * Create a commands file by concatenating commands.
	 * 
	 * @param inPreCommands
	 * @param inCommands
	 * @return
	 * @throws IOException
	 */
	private static File createCmdsFile(String... inCommands) throws IOException {

		/* create a temporary file */
		File tmp = File.createTempFile("sagent", "cmds");
		tmp.deleteOnExit();

		PrintWriter pw = new PrintWriter(new FileWriter(tmp));

		for (String cmd : inCommands) {
			logger.info("Added command: " + cmd);
			pw.println(cmd);
		}
		pw.close();

		return tmp;
	}


	public static String commandCreateStrategy(
			String inName,							// module name
			String inClassName,						// class name (no qualifiers)
			Language inLanguage,					// language
			String inFileName,						// path to source
			Properties inParameters,				// parameters to strategy
			boolean routeToORS,						// route orders?
			ModuleURN inDestination) {				// destination of flow
		StringBuilder createCommand = new StringBuilder("createModule;");
		createCommand.append(StrategyModuleFactory.PROVIDER_URN).append(';');
		if (inName != null) {
			createCommand.append(inName);
		}
		createCommand.append(COMMA);
		createCommand.append(inClassName).append(COMMA).
		append(inLanguage.toString()).append(COMMA).
		append(inFileName).append(COMMA);
		if (inParameters != null && !inParameters.isEmpty()) {
			for(Map.Entry entry: inParameters.entrySet()) {
				createCommand.append(entry.getKey()).append('=').
				append(entry.getValue()).append(':');
			}
		}
		createCommand.append(COMMA).append(routeToORS).append(COMMA);
		if (inDestination != null) {
			createCommand.append(inDestination.toString());
		}
		return createCommand.toString();
	}

	public static String commandStart(String inURN) {
		return new StringBuilder("startModule;").append(inURN).toString();
	}

	public static ModuleManagerMXBean getManagerBean()
		throws MalformedObjectNameException {
		return JMX.newMXBeanProxy(getBeanServer(),
				new ObjectName(ModuleManager.MODULE_MBEAN_NAME),
				ModuleManagerMXBean.class);
	}

	public static MBeanServer getBeanServer() {
		return ManagementFactory.getPlatformMBeanServer();
	}

	private static String strategyURN(String inStrategyName) {
		return new ModuleURN(StrategyModuleFactory.PROVIDER_URN, 
				inStrategyName).toString();
	}
		
	public static void startStrategy( String strategyName ) throws MalformedObjectNameException, RuntimeException {
		getManagerBean().start(strategyURN(strategyName));		
	}

	public static void stopStrategy( String strategyName ) throws MalformedObjectNameException, RuntimeException {
		getManagerBean().stop(strategyURN(strategyName));		
	}

	
	private static class StrategyAgentProxy extends StrategyAgent {
        @Override
        public void startWaitingForever() {
            //do nothing, return right away
        }
    }
}
