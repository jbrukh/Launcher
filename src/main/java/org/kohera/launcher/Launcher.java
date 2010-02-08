package org.kohera.launcher;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.Configurator;
import org.marketcetera.strategy.Language;
import org.marketcetera.strategyagent.StrategyAgent;

/**
 * Class for automatically launching strategies and data modules via
 * StrategyAgent.
 * 
 * Command Line Args
 * 
 * 	Launcher [configDir] [strategySrc] [dataURN] [strategyClass] [stratParam]
 * 
 * [configDir] 		-- directory with the SA instance configuration/modules
 * [strategySrc] 	-- root directory for the strategy src (i.e src/java/main)
 * [dataURN]		-- data module URN (i.e. metc:mdata:bogus:single)
 * [strategyClass]  -- full class name (i.e. org.kohera.strategies.SuperStrat)
 * [stratParam]		-- path to .properties file containing strategy parameters
 * 
 * All command-line arguments are mandatory except for [stratParam].
 * 
 * Example:
 * 
 * Create launcher/					
 * 			config/				# dir with account configs
 * 				account1/
 * 					conf/		# account config
 * 					modules/	# modules config
 * 				account2/
 * 					...
 * 			lib/				# all dependencies
 * 			src/				# strategy source
 *				main/
 *					java/		# strategy source root
 *						org/
 *							...
 *
 * Set up a bash script to run the launcher:
 * 
 *   #!/bin/bash
 *   
 *   JAVA_CP=./conf
 *   for jarfile in $(ls ./lib)
 *   do
 *     JAVA_CP="$JAVA_CP;./lib/$jarfile"
 *   done;
 *   
 *   java -cp "$JAVA_CP" org.kohera.launcher.Launcher [[[YOUR ARGS]]] 
 *   tail -f logs/launcher.log
 *
 * Example args for the above setup:
 * 
 * Launcher config/account1 \
 * 			src/main/java \
 *          metc:mdata:bogus:single \
 *          org.kohera.strategies.SuperStrat \
 *          config/account1/conf/SuperStrat.properties
 *
 *
 */							
 	
public class Launcher {

	static {
		PropertyConfigurator.configure("log4j.properties");
	}
	
	/* logging */
	private final static Logger logger =
		Logger.getLogger(Launcher.class);
	
	/* fields */
	private String configDir;			// relative to root, the StrategyAgent config, something like ./config/my_conf
	private String sourceRoot;			// to the root of the strategy source, something like ./MyStrategy/src/main/java/ 
	private String dataModuleURN;		// data module URN, such as "metc:mdata:bogus:single"
	private String strategyClass;		// full name of the class, i.e. org.myorg.SuperDuperStrat
	private Properties params;			// parameters to the strategy

	private String token;
	
	private StrategyAgent agent;		// agent
	
	private static final String SEP 	= "/";
	/**
	 * Create a new instance of the Launcher with parameters.
	 * 
	 * @param configDir
	 * @param dataModuleURN
	 * @param paramFile
	 */
	public Launcher( String configDir, String sourceRoot, 
			String dataModuleURN, String strategyClass, String paramFile ) {
		/* set the system property for the StrategyAgent */
		System.setProperty("org.marketcetera.appDir", configDir);
		
		/* instantiate fields */
		this.params 		= new Properties();
		this.configDir 		= fixPath(configDir);
		this.dataModuleURN 	= dataModuleURN;
		this.sourceRoot 	= fixPath(sourceRoot);
		this.strategyClass 	= strategyClass;
		this.token		 	= getNameToken();

		/* read the parameters, if applicable */
		if ( paramFile != null ) {
			readParameters(paramFile);
		}
		
		/* logging */
		logger.info("Starting Launcher...");
		logger.info("workingDir     = " + System.getProperty("user.dir"));
		logger.info("dataModuleURN  = " + this.dataModuleURN);
		logger.info("configDir      = " + this.configDir);
		logger.info("sourceRoot     = " + this.sourceRoot);
		logger.info("strategyClass 	= " + this.strategyClass);
		logger.info("params         = " + this.params.toString() );
		
	}

	/**
	 * Create a new instance of the Launcher.
	 * 
	 * @param configDir
	 * @param dataModuleURN
	 */
	public Launcher( String configDir, String sourceRoot, 
			String dataModuleURN, String strategyClass ) {
		this(configDir,sourceRoot,dataModuleURN,strategyClass,null);
	}
	
	public void launch() throws Exception {
		logger.info("Launching StrategyAgent...");
		agent = StrategyAgentUtils.startAgent(
						getCreateStrategyCommand(),
						getCreateDataFeedCommand());
		logger.info("Starting " + strategyClass + "...");
		StrategyAgentUtils.startStrategy(token);
	}
	
	// MAIN //
	
	public static void main( String[] args ) {
		
		if (args.length<4) {
			logger.error("USAGE: java <configDir> " +
					"<strategy src root> <data module URN> <strategy class> <parameters file>");
			return;
		}
		
		Launcher launcher = new Launcher(
				args[0],
				args[1],
				args[2],
				args[3],
				(args.length==5?args[4]:null));
		
		try {
			launcher.launch();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}


	// PRIVATE //
	
	private final String getCreateStrategyCommand() {
		/* i.e. -- MyStrategy.java */
		String javaName = token + ".java";
		/* i.e. -- ./src/main/java/org/myorg/SuperDuper/MyStrategy.java */
		String srcPath = getSourcePath()+javaName;
		
		return StrategyAgentUtils.commandCreateStrategy(
				token, 
				token, 
				Language.JAVA, 
				srcPath, 
				params, 
				true, 
				StrategyAgentUtils.DESTINATION_SINK);
	}
	
	private final String getCreateDataFeedCommand() {
		return StrategyAgentUtils.commandStart(dataModuleURN);
	}
	
	
	private final String getNameToken() {
		String[] tokens = strategyClass.split("\\.");
		int len = tokens.length;
		return tokens[len-1];
	}
	
	private final String getSourcePath() {
		StringBuilder path = new StringBuilder();
		String[] tokens = strategyClass.split("\\.");
		int len = tokens.length;
		
		path.append(sourceRoot);
		for ( int i = 0; i < len-1; i++ ) {
			path.append(tokens[i]).append(SEP);
		}
		return path.toString();
	}
	
	/**
	 * Read the parameters in from a file.
	 */
	private final void readParameters( String paramFile ) {

		InputStream in = null;
		try {
			in = new FileInputStream(paramFile);
			params.load(in);
		} catch ( IOException e ) {
			logger.error("Could not read the strategy parameters file: " + paramFile);
		} finally {
			try {
				if ( in!=null ) in.close();
			} catch (IOException e) { e.printStackTrace(); }
		}
	}
	
	private final String fixPath(String path) {
		if ( !path.endsWith(SEP) && !path.endsWith("\\") ) {
			return path+SEP;
		}
		return path;
	}

}
