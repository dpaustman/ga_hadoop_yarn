package com.dic.distributedga.yarn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;

import com.dic.distributedga.Utils;

public class Client {
	private static final Log log = LogFactory.getLog(Client.class);
	private static final String appMasterJarHDFSDesPath = "AppMaster.jar";
	private static final String log4jHDFSDesPath = "log4j.properties";
	public static final String USER_GA_JAR_NAME = "ga.jar";

	private Options opts;
	private boolean debugFlag;
	private String log4jPropFile;
	private String appName;
	private int amMemory;
	private int amVCores;
	private String amJarPath;
	private int containerMemory;
	private int containerVirtualCores;
	private int numContainers;

	private Configuration conf;
	private String amMainClass;
	private YarnClient yarnClient;

	public static void main(String[] args) {
		boolean result = false;
		Client client = new Client();

		try {

			client.init(args);
			result = client.run();

		} catch (ParseException e) {
			e.printStackTrace();
			log.fatal("Error running client");
			System.exit(2);
		} catch (IllegalArgumentException e) {
			e.getLocalizedMessage();
			client.printUsage();
			System.exit(2);
		} catch (YarnException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (result) {
			log.info("Application completed successfully");
			System.exit(0);
		}

		log.info("Applicatoin failed to complete successfully");
		System.exit(1);

	}

	public Client() {
		this(new YarnConfiguration());
	}

	public Client(Configuration config) {
		this("com.dic.distributedga.yarn.ApplicationMasterGA", config);
	}

	public Client(String amMainClass, Configuration config) {

		this.conf = config;
		this.amMainClass = amMainClass;
		yarnClient = YarnClient.createYarnClient();
		yarnClient.init(conf);
		opts = new Options();
		opts.addOption("appname", true, "Application name. Default value - DistributedGA");
		opts.addOption("master_memory", true, "Amount of memory in MB to be requested to run the application master");
		opts.addOption("master_vcores", true, "Amount of virtual cores to be requested to run the application master");
		opts.addOption("jar", true, "Jar file containing the application master");
		opts.addOption("container_memory", true, "Amount of memory in MB to be requested to run the shell command");
		opts.addOption("container_vcores", true, "Amount of virtual cores to be requested to run the shell command");
		opts.addOption("num_containers", true, "No. of containers on which the shell command needs to be executed");
		opts.addOption("debug", false, "Dump out debug information");
		opts.addOption("help", false, "Print usage");
	}

	public boolean init(String[] args) throws ParseException {
		log.info("Initializing client");
		CommandLine cliParser = new GnuParser().parse(opts, args);

		if (args.length == 0) {
			throw new IllegalArgumentException("No args specified for client to initialize");
		}

		if (cliParser.hasOption("help")) {
			printUsage();
			return false;
		}

		if (!cliParser.hasOption("jar")) {
			throw new IllegalArgumentException("No jar file specified for application master");
		}

		debugFlag = Boolean.parseBoolean(cliParser.getOptionValue("debug", "false"));
		appName = cliParser.getOptionValue("appname", "DistributedGA");
		amJarPath = cliParser.getOptionValue("jar");
		amMemory = Integer.parseInt(cliParser.getOptionValue("master_memory", "10"));
		amVCores = Integer.parseInt(cliParser.getOptionValue("master_vcores", "1"));

		if (amMemory <= 0) {
			throw new IllegalArgumentException(
					"Invalid memory specified for application master, exiting." + " Specified memory=" + amMemory);
		}
		if (amVCores <= 0) {
			throw new IllegalArgumentException("Invalid virtual cores specified for application master, exiting."
					+ " Specified virtual cores=" + amVCores);
		}

		containerMemory = Integer.parseInt(cliParser.getOptionValue("container_memory", "10"));
		containerVirtualCores = Integer.parseInt(cliParser.getOptionValue("container_vcores", "1"));
		numContainers = Integer.parseInt(cliParser.getOptionValue("num_containers", "1"));

		if (containerMemory < 0 || containerVirtualCores < 0 || numContainers < 1) {
			throw new IllegalArgumentException("Invalid no. of containers or container memory/vcores specified,"
					+ " exiting." + " Specified containerMemory=" + containerMemory + ", containerVirtualCores="
					+ containerVirtualCores + ", numContainer=" + numContainers);
		}

		log4jPropFile = cliParser.getOptionValue("log_porperties", "");
		return true;
	}

	private void printUsage() {
		new HelpFormatter().printHelp("Client", opts);
	}

	public boolean run() throws YarnException, IOException {
		log.info("Running client");
		yarnClient.start();

		YarnClientApplication app = yarnClient.createApplication();
		GetNewApplicationResponse appResponse = app.getNewApplicationResponse();

		int maxMem = appResponse.getMaximumResourceCapability().getMemory();
		log.info("Max mem capabililty of resources in this cluster " + maxMem);

		// A resource ask cannot exceed the max.
		if (amMemory > maxMem) {
			log.info("AM memory specified above max threshold of cluster. Using max value." + ", specified=" + amMemory
					+ ", max=" + maxMem);
			amMemory = maxMem;
		}

		int maxVCores = appResponse.getMaximumResourceCapability().getVirtualCores();
		log.info("Max virtual cores capabililty of resources in this cluster " + maxVCores);

		if (amVCores > maxVCores) {
			log.info("AM virtual cores specified above max threshold of cluster. " + "Using max value." + ", specified="
					+ amVCores + ", max=" + maxVCores);
			amVCores = maxVCores;
		}

		ApplicationSubmissionContext appContext = app.getApplicationSubmissionContext();
		ApplicationId appId = appContext.getApplicationId();

		appContext.setApplicationName(appName);

		Map<String, LocalResource> localResources = new HashMap<String, LocalResource>();
		FileSystem fs = FileSystem.get(conf);

		log.info("Copy app master jar from local file system and add to local environment");
		addToLocalResources(fs, amJarPath, appMasterJarHDFSDesPath, appId.toString(), localResources, null);

		if (!log4jPropFile.isEmpty()) {
			addToLocalResources(fs, log4jPropFile, log4jHDFSDesPath, appId.toString(), localResources, null);
		}

		String hdfsGAJarLocation = "";
		long hdfsGAJarLen = 0;
		long hdfsGAJarTimestamp = 0;
		if (!amJarPath.isEmpty()) {
			Path userGAJarSrc = new Path(amJarPath);
			String userGAJarPathSuffix = appName + "/" + appId.toString() + "/" + USER_GA_JAR_NAME;
			Path userGAJarHDFSDst = new Path(fs.getHomeDirectory(), userGAJarPathSuffix);
			fs.copyFromLocalFile(false, true, userGAJarSrc, userGAJarHDFSDst);
			hdfsGAJarLocation = userGAJarHDFSDst.toUri().toString();
			FileStatus hdfsGAJarFileStatus = fs.getFileStatus(userGAJarHDFSDst);
			hdfsGAJarLen = hdfsGAJarFileStatus.getLen();
			hdfsGAJarTimestamp = hdfsGAJarFileStatus.getModificationTime();
		}

		// Set the env variable where AM will run
		log.info("Set the environment on node where application master will run");
		Map<String, String> env = new HashMap<String, String>();

		// put the location of user ga jar which will run on slave nodes.
		env.put(Utils.USER_GA_JAR_HDFS_LOC, hdfsGAJarLocation);
		env.put(Utils.USER_GA_JAR_HDFS_TIMESTAMP, Long.toString(hdfsGAJarTimestamp));
		env.put(Utils.USER_GA_JAR_HDFS_LEN, Long.toString(hdfsGAJarLen));

		// environment.classpath is yarn.application.classpath in
		// yarn-default.xml
		// all jar files needed for running an app in yarn.
		StringBuilder classPathEnv = new StringBuilder(Environment.CLASSPATH.$$())
				.append(ApplicationConstants.CLASS_PATH_SEPARATOR).append("./*");

		for (String c : conf.getStrings(YarnConfiguration.YARN_APPLICATION_CLASSPATH,
				YarnConfiguration.DEFAULT_YARN_CROSS_PLATFORM_APPLICATION_CLASSPATH)) {
			classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR);
			classPathEnv.append(c.trim());
		}

		classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR).append("./log4j.properties");

		if (conf.getBoolean(YarnConfiguration.IS_MINI_YARN_CLUSTER, false)) {
			classPathEnv.append(':');
			classPathEnv.append(System.getProperty("java.class.path"));
		}

		env.put("CLASSPATH", classPathEnv.toString());
		log.info("classpath env variable");
		log.info(classPathEnv.toString());

		ArrayList<CharSequence> vargs = new ArrayList<CharSequence>(30);

		// set java executable command
		log.info("set up master command");
		vargs.add(Environment.JAVA_HOME.$$() + "/bin/java");
		vargs.add("-Xms" + amMemory + "m");
		vargs.add(amMainClass);
		vargs.add("--container_memory " + String.valueOf(containerMemory));
		vargs.add("--container_vcores " + String.valueOf(containerVirtualCores));
		vargs.add("--num_containers " + String.valueOf(numContainers));

		if (debugFlag) {
			vargs.add("--debug");
		}

		vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stdout");
		vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stderr");

		// final command
		StringBuilder command = new StringBuilder();
		for (CharSequence str : vargs) {
			command.append(str).append(" ");
		}

		log.info("completed app master command ");
		log.info(command.toString());
		List<String> commands = new ArrayList<String>();
		commands.add(command.toString());

		// container launch context for app master
		ContainerLaunchContext amContainer = ContainerLaunchContext.newInstance(localResources, env, commands, null,
				null, null);

		Resource capability = Resource.newInstance(amMemory, amVCores);
		appContext.setResource(capability);

		if (UserGroupInformation.isSecurityEnabled()) {
			// Note: Credentials class is marked as LimitedPrivate for HDFS and
			// MapReduce
			Credentials credentials = new Credentials();
			String tokenRenewer = conf.get(YarnConfiguration.RM_PRINCIPAL);
			if (tokenRenewer == null || tokenRenewer.length() == 0) {
				throw new IOException("Can't get Master Kerberos principal for the RM to use as renewer");
			}

			// For now, only getting tokens for the default file-system.
			final Token<?> tokens[] = fs.addDelegationTokens(tokenRenewer, credentials);
			if (tokens != null) {
				for (Token<?> token : tokens) {
					log.info("Got dt for " + fs.getUri() + "; " + token);
				}
			}
			DataOutputBuffer dob = new DataOutputBuffer();
			credentials.writeTokenStorageToStream(dob);
			ByteBuffer fsTokens = ByteBuffer.wrap(dob.getData(), 0, dob.getLength());
			amContainer.setTokens(fsTokens);
		}

		appContext.setAMContainerSpec(amContainer);
		// set up process complete at this point now client is ready to submit
		// application.

		log.info("submitting application to ");

		yarnClient.submitApplication(appContext);

		return monitorApplication(appId);

	}

	public void addToLocalResources(FileSystem fs, String fileSrcPath, String fileDstPath, String appId,
			Map<String, LocalResource> localResources, String resources) throws IOException {
		String suffix = appName + "/" + "appId" + "/" + fileDstPath;
		Path dst = new Path(fs.getHomeDirectory(), suffix);

		if (fileSrcPath == null) {
			FSDataOutputStream ostream = null;

			try {
				ostream = FileSystem.create(fs, dst, new FsPermission((short) 0710));
				ostream.writeUTF(resources);
			} finally {
				IOUtils.closeQuietly(ostream);
			}

		} else {
			fs.copyFromLocalFile(new Path(fileSrcPath), dst);
		}

		FileStatus scFileStatus = fs.getFileStatus(dst);
		LocalResource scRsrc = LocalResource.newInstance(ConverterUtils.getYarnUrlFromURI(dst.toUri()),
				LocalResourceType.FILE, LocalResourceVisibility.APPLICATION, scFileStatus.getLen(),
				scFileStatus.getModificationTime());
		localResources.put(fileDstPath, scRsrc);
	}

	public boolean monitorApplication(ApplicationId appId) throws YarnException, IOException {
		while (true) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			ApplicationReport report = yarnClient.getApplicationReport(appId);
			
		      log.info("Got application report from ASM for"
		              + ", appId=" + appId.getId()
		              + ", clientToAMToken=" + report.getClientToAMToken()
		              + ", appDiagnostics=" + report.getDiagnostics()
		              + ", appMasterHost=" + report.getHost()
		              + ", appQueue=" + report.getQueue()
		              + ", appMasterRpcPort=" + report.getRpcPort()
		              + ", appStartTime=" + report.getStartTime()
		              + ", yarnAppState=" + report.getYarnApplicationState().toString()
		              + ", distributedFinalState=" + report.getFinalApplicationStatus().toString()
		              + ", appTrackingUrl=" + report.getTrackingUrl()
		              + ", appUser=" + report.getUser());
		      
		      
		      
		      YarnApplicationState state = report.getYarnApplicationState();
		      FinalApplicationStatus dsStatus = report.getFinalApplicationStatus();
		      if (YarnApplicationState.FINISHED == state) {
		        if (FinalApplicationStatus.SUCCEEDED == dsStatus) {
		          log.info("Application has completed successfully. Breaking monitoring loop");
		          return true;        
		        }
		        else {
		          log.info("Application did finished unsuccessfully."
		              + " YarnState=" + state.toString() + ", DSFinalStatus=" + dsStatus.toString()
		              + ". Breaking monitoring loop");
		          return false;
		        }			  
		      }
		      else if (YarnApplicationState.KILLED == state	
		          || YarnApplicationState.FAILED == state) {
		        log.info("Application did not finish."
		            + " YarnState=" + state.toString() + ", DSFinalStatus=" + dsStatus.toString()
		            + ". Breaking monitoring loop");
		        return false;
		      }			
		}
	}
}
