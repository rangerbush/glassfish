/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.enterprise.glassfish.bootstrap;

import com.sun.enterprise.module.bootstrap.ArgumentManager;
import com.sun.enterprise.module.bootstrap.PlatformMain;
import com.sun.enterprise.module.bootstrap.StartupContext;
import com.sun.enterprise.module.bootstrap.Which;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class used by bootstrap module.
 * Most of the code is moved from {@link ASMain} or {@link GlassFishMain}to this class to keep them
 * as small as possible and to improve reusability when GlassFish is launched in other modes (e.g., karaf).
 *
 * @author Sanjeeb.Sahoo@Sun.COM
 */
class ASMainHelper {

    private static Logger logger = Logger.getLogger(ASMainHelper.class.getPackage().getName());

    /*protected*/

    static void checkJdkVersion() {
        int minor = getMinorJdkVersion();

        if (minor < 6) {
            logger.severe("GlassFish requires JDK 6, you are using JDK version " + minor);
            System.exit(1);
        }
    }

    private static int getMinorJdkVersion() {
        // this is a subset of the code in com.sun.enterprise.util.JDK
        // this module has no dependencies on util code so it was dragged in here.

        try {
            String jv = System.getProperty("java.version");
            String[] ss = jv.split("\\.");

            if (ss == null || ss.length < 3 || !ss[0].equals("1"))
                return 1;

            return Integer.parseInt(ss[1]);
        }
        catch (Exception e) {
            return 1;
        }
    }

    static String whichPlatform() {
        String platform = Constants.Platform.Felix.toString(); // default is Felix

        // first check the system props
        String temp = System.getProperty(Constants.PLATFORM_PROPERTY_KEY);
        if (temp == null || temp.trim().length() <= 0) {
            // not in sys props -- check environment
            temp = System.getenv(Constants.PLATFORM_PROPERTY_KEY);
        }

        if (temp != null && temp.trim().length() != 0) {
            platform = temp.trim();
        }
        return platform;
    }

    /**
     * use META-INF/services services definition to look up all possible platform implementations
     * and return the one
     *
     * @param platform the platform name {@see AbstractMain#getName()}
     * @return an platform provider or null if not found
     */
    static PlatformMain getMain(String platform) {
        ServiceLoader<PlatformMain> loader = ServiceLoader.load(PlatformMain.class, ASMain.class.getClassLoader());
        for (PlatformMain main : loader) {
            if (main.getName().equalsIgnoreCase(platform))
                return main;
        }
        return null;
    }

    static Properties parseAsEnv(File installRoot) {

        Properties asenvProps = new Properties();

        // let's read the asenv.conf
        File configDir = new File(installRoot, "config");
        File asenv = getAsEnvConf(configDir);

        if (!asenv.exists()) {
            logger.fine(asenv.getAbsolutePath() + " not found, ignoring");
            return asenvProps;
        }
        LineNumberReader lnReader = null;
        try {
            lnReader = new LineNumberReader(new FileReader(asenv));
            String line = lnReader.readLine();
            // most of the asenv.conf values have surrounding "", remove them
            // and on Windows, they start with SET XXX=YYY
            Pattern p = Pattern.compile("[Ss]?[Ee]?[Tt]? *([^=]*)=\"?([^\"]*)\"?");
            while (line != null) {
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    File f = new File(m.group(2));
                    if (!f.isAbsolute()) {
                        f = new File(configDir, m.group(2));
                        if (f.exists()) {
                            asenvProps.put(m.group(1), f.getAbsolutePath());
                        } else {
                            asenvProps.put(m.group(1), m.group(2));
                        }
                    } else {
                        asenvProps.put(m.group(1), m.group(2));
                    }
                }
                line = lnReader.readLine();
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Error opening asenv.conf : ", ioe);
        } finally {
            try {
                if (lnReader != null)
                    lnReader.close();
            } catch (IOException ioe) {
                // ignore
            }
        }
        return asenvProps;
    }

    void addPaths(File dir, String[] jarPrefixes, List<URL> urls) throws MalformedURLException {
        File[] jars = dir.listFiles();
        if (jars != null) {
            for (File f : jars) {
                for (String prefix : jarPrefixes) {
                    String name = f.getName();
                    if (name.startsWith(prefix) && name.endsWith(".jar"))
                        urls.add(f.toURI().toURL());
                }
            }
        }
    }

    /**
     * Figures out the asenv.conf file to load.
     */
    private static File getAsEnvConf(File configDir) {
        String osName = System.getProperty("os.name");
        if (osName.indexOf("Windows") == -1) {
            return new File(configDir, "asenv.conf");
        } else {
            return new File(configDir, "asenv.bat");
        }
    }

    /**
     * Determines the root directory of the domain that we'll start.
     */
    /*package*/ static File getDomainRoot(Properties args, Properties asEnv) {
        // first see if it is specified directly

        String domainDir = getParam(args, "domaindir");

        if (ok(domainDir))
            return new File(domainDir);

        // now see if they specified the domain name -- we will look in the
        // default domains-dir

        File defDomainsRoot = getDefaultDomainsDir(asEnv);
        String domainName = getParam(args, "domain");

        if (ok(domainName))
            return new File(defDomainsRoot, domainName);

        // OK -- they specified nothing.  Get the one-and-only domain in the
        // domains-dir
        return getDefaultDomain(defDomainsRoot);
    }

    /**
     * Verifies correctness of the root directory of the domain that we'll start and
     * sets the system property called {@link com.sun.enterprise.glassfish.bootstrap.Constants#INSTANCE_ROOT_PROP_NAME}.
     */
    /*package*/ void verifyAndSetDomainRoot(File domainRoot) {
        verifyDomainRoot(domainRoot);

        domainRoot = absolutize(domainRoot);
        System.setProperty(Constants.INSTANCE_ROOT_PROP_NAME, domainRoot.getPath());
    }

    /**
     * Verifies correctness of the root directory of the domain that we'll start.
     *
     * @param domainRoot
     */
    /*package*/
    static void verifyDomainRoot(File domainRoot) {
        String msg = null;

        if (domainRoot == null)
            msg = "Internal Error: The domain dir is null.";
        else if (!domainRoot.exists())
            msg = "the domain directory does not exist";
        else if (!domainRoot.isDirectory())
            msg = "the domain directory is not a directory.";
        else if (!domainRoot.canWrite())
            msg = "the domain directory is not writable.";
        else if (!new File(domainRoot, "config").isDirectory())
            msg = "the domain directory is corrupt - there is no config subdirectory.";

        if (msg != null)
            throw new RuntimeException(msg);
    }

    private static File getDefaultDomainsDir(Properties asEnv) {
        // note: 99% error detection!

        String dirname = asEnv.getProperty(Constants.DEFAULT_DOMAINS_DIR_PROPNAME);

        if (!ok(dirname))
            throw new RuntimeException(Constants.DEFAULT_DOMAINS_DIR_PROPNAME + " is not set.");

        File domainsDir = absolutize(new File(dirname));

        if (!domainsDir.isDirectory())
            throw new RuntimeException(Constants.DEFAULT_DOMAINS_DIR_PROPNAME +
                    "[" + dirname + "]" +
                    " is specifying a file that is NOT a directory.");

        return domainsDir;
    }


    private static File getDefaultDomain(File domainsDir) {
        File[] domains = domainsDir.listFiles(new FileFilter() {
            public boolean accept(File f) { return f.isDirectory(); }
        });

        // By default we will start an unspecified domain iff it is the only
        // domain in the default domains dir

        if (domains == null || domains.length == 0)
            throw new RuntimeException("no domain directories found under " + domainsDir);

        if (domains.length > 1)
            throw new RuntimeException("Multiple domains[" + domains.length + "] found under "
                    + domainsDir + " -- you must specify a domain name as -domain <name>");

        return domains[0];
    }


    private static boolean ok(String s) {
        return s != null && s.length() > 0;
    }

    private static String getParam(Properties map, String name) {
        // allow both "-" and "--"
        String val = map.getProperty("-" + name);

        if (val == null)
            val = map.getProperty("--" + name);

        return val;
    }

    private static File absolutize(File f) {
        try {
            return f.getCanonicalFile();
        }
        catch (Exception e) {
            return f.getAbsoluteFile();
        }
    }

    /**
     * CLI or any other client needs to ALWAYS pass in the instanceDir for
     * instances.
     *
     * @param args
     * @param asEnv
     * @return
     */
    static File getInstanceRoot(Properties args, Properties asEnv) {

        String instanceDir = getParam(args, "instancedir");

        if (ok(instanceDir))
            return new File(instanceDir);

        return null;
    }

    /* package */

    static File findInstallRoot() {
        File bootstrapFile = findBootstrapFile(); // glassfish/modules/glassfish.jar
        return bootstrapFile.getParentFile().getParentFile(); // glassfish/
    }

    /* package */

    static File findInstanceRoot(File installRoot, Properties args) {
        Properties asEnv = parseAsEnv(installRoot);

        // IMPORTANT - check for instance BEFORE domain.  We will always come up
        // with a default domain but there is no such thing sa a default instance

        File instanceDir = getInstanceRoot(args, asEnv);

        if (instanceDir == null) {
            // that means that this is a DAS.
            instanceDir = getDomainRoot(args, asEnv);
        }
        verifyDomainRoot(instanceDir);
        return instanceDir;
    }

    static File findInstanceRoot(File installRoot, String[] args) {
        return findInstanceRoot(installRoot, ArgumentManager.argsToMap(args));
    }

    private static File findBootstrapFile() {
        try {
            return Which.jarFile(ASMain.class);
        } catch (IOException e) {
            throw new RuntimeException("Cannot get bootstrap path from "
                    + ASMain.class + " class location, aborting");
        }
    }

    static Properties buildStartupContext(String platform, File installRoot, File instanceRoot, String[] args) {
        Properties ctx = com.sun.enterprise.module.bootstrap.ArgumentManager.argsToMap(args);
        ctx.setProperty(StartupContext.TIME_ZERO_NAME, (new Long(System.currentTimeMillis())).toString());

        ctx.setProperty(Constants.PLATFORM_PROPERTY_KEY, platform);

        ctx.setProperty(Constants.INSTALL_ROOT_PROP_NAME, installRoot.getAbsolutePath());
        ctx.setProperty(Constants.INSTALL_ROOT_URI_PROP_NAME, installRoot.toURI().toString());

        ctx.setProperty(Constants.INSTANCE_ROOT_PROP_NAME, instanceRoot.getAbsolutePath());
        ctx.setProperty(Constants.INSTANCE_ROOT_URI_PROP_NAME, instanceRoot.toURI().toString());

        if (ctx.getProperty(StartupContext.STARTUP_MODULE_NAME) == null) {
            ctx.setProperty(StartupContext.STARTUP_MODULE_NAME, Constants.GF_KERNEL);
        }

        // temporary hack until CLI does that for us.
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-upgrade")) {
                if (i + 1 < args.length && !args[i + 1].equals("false")) {
                    ctx.setProperty(StartupContext.STARTUP_MODULESTARTUP_NAME, "upgrade");
                }
            }
        }

        addRawStartupInfo(args, ctx);

        mergePlatformConfiguration(ctx);
        return ctx;
    }

    static void buildStartupContext(Properties ctx) {
        if (ctx.getProperty(StartupContext.TIME_ZERO_NAME) == null) {
            ctx.setProperty(StartupContext.TIME_ZERO_NAME, (new Long(System.currentTimeMillis())).toString());
        } else {
            // Optimisation
            // Skip the rest of the code. We assume that we are called from GlassFishMain
            // which already passes a properly populated properties object.
            return;
        }

        if (ctx.getProperty(Constants.PLATFORM_PROPERTY_KEY) == null) {
            ctx.setProperty(Constants.PLATFORM_PROPERTY_KEY, Constants.Platform.Felix.name());
        }

        if (ctx.getProperty(Constants.INSTALL_ROOT_PROP_NAME) == null) {
            File installRoot = findInstallRoot();
            ctx.setProperty(Constants.INSTALL_ROOT_PROP_NAME, installRoot.getAbsolutePath());
            ctx.setProperty(Constants.INSTALL_ROOT_URI_PROP_NAME, installRoot.toURI().toString());
        }

        if (ctx.getProperty(Constants.INSTANCE_ROOT_PROP_NAME) == null) {
            File installRoot = new File(ctx.getProperty(Constants.INSTALL_ROOT_PROP_NAME));
            File instanceRoot = findInstanceRoot(installRoot, ctx);
            ctx.setProperty(Constants.INSTANCE_ROOT_PROP_NAME, instanceRoot.getAbsolutePath());
            ctx.setProperty(Constants.INSTANCE_ROOT_URI_PROP_NAME, instanceRoot.toURI().toString());
        }

        if (ctx.getProperty(StartupContext.STARTUP_MODULE_NAME) == null) {
            ctx.setProperty(StartupContext.STARTUP_MODULE_NAME, Constants.GF_KERNEL);
        }

        mergePlatformConfiguration(ctx);
    }

    /**
     * Need the raw unprocessed args for RestartDomainCommand in case we were NOT started
     * by CLI
     *
     * @param args raw args to this main()
     * @param p    the properties to save as a system property
     */
    private static void addRawStartupInfo(final String[] args, final Properties p) {
        //package the args...
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < args.length; i++) {
            if (i > 0)
                sb.append(Constants.ARG_SEP);

            sb.append(args[i]);
        }

        if (!wasStartedByCLI(p)) {
            // no sense doing this if we were started by CLI...
            p.put(Constants.ORIGINAL_CP, System.getProperty("java.class.path"));
            p.put(Constants.ORIGINAL_CN, ASMain.class.getName());
            p.put(Constants.ORIGINAL_ARGS, sb.toString());
        }
    }

    private static boolean wasStartedByCLI(final Properties props) {
        // if we were started by CLI there will be some special args set...

        return
                props.getProperty("-asadmin-classpath") != null &&
                        props.getProperty("-asadmin-classname") != null &&
                        props.getProperty("-asadmin-args") != null;
    }

    /**
     * This method is responsible for setting up the launcher class loader and
     * setting the context class loader as well.
     * Launcher class loader is used to load jdk tools.jar, derby classes (why?) and
     * OSGi framework classes) and glassfish.jar, which contains glassfish bootstrap API classes.
     * Our hierarchy looks like this:
     *
     * @param delegate: Parent class loader for the launcher
     */
    static ClassLoader createLauncherCL(Properties ctx, ClassLoader delegate) {
        try {
            ClassLoaderBuilder clb = new ClassLoaderBuilder(ctx, delegate);
            clb.addLauncherJar();
            clb.addFrameworkJars();
            clb.addJDKToolsJar();
            clb.findDerbyClient();
            return clb.build();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    /**
     * Store relevant information in system properties.
     *
     * @param ctx
     */
    static void setSystemProperties(Properties ctx) {
        // Set the system property if downstream code wants to know about it
        System.setProperty(Constants.PLATFORM_PROPERTY_KEY, ctx.getProperty(Constants.PLATFORM_PROPERTY_KEY));
    }

    static void mergePlatformConfiguration(Properties ctx) {
        Properties platformConf = null;
        try {
            platformConf = PlatformHelper.getPlatformHelper(ctx).readPlatformConfiguration();
        } catch (IOException e) {
            throw new RuntimeException(e); // TODO(Sahoo): Proper Exception Handling
        }
        platformConf.putAll(ctx);
        Util.substVars(platformConf);
        ctx.clear();
        ctx.putAll(platformConf);
    }

    static boolean isOSGiPlatform(String platform) {
        Constants.Platform p = Constants.Platform.valueOf(platform);
        switch (p) {
            case Felix:
            case Knopflerfish:
            case Equinox:
                return true;
        }
        return false;
    }

    static class ClassLoaderBuilder {

        protected ClassPathBuilder cpb;

        protected File glassfishDir;

        protected Properties ctx;

        ClassLoaderBuilder(Properties ctx, ClassLoader delegate) {
            this.ctx = ctx;
            cpb = new ClassPathBuilder(delegate);
            glassfishDir = new File(ctx.getProperty(Constants.INSTALL_ROOT_PROP_NAME));
        }

        void addFrameworkJars() throws IOException {
            PlatformHelper.getPlatformHelper(ctx).addFrameworkJars(cpb);
        }

        /**
         * Adds JDK tools.jar to classpath.
         */
        void addJDKToolsJar() {
            try {

                File jdkToolsJar = Util.getJDKToolsJar();
                if (jdkToolsJar.exists()) {
                    cpb.addJar(jdkToolsJar);
                } else {
                    // on the mac, it happens all the time
                    logger.fine("JDK tools.jar does not exist at " + jdkToolsJar);
                }
            } catch (IOException e) {
                throw new Error(e);
            }
        }

        void findDerbyClient() throws IOException {
            // Sahoo: Why do we have to add derby to this class loader? Find out from Jerome.
            String derbyHome = parseAsEnv(glassfishDir).getProperty("AS_DERBY_INSTALL");
            File derbyLib = null;
            if (derbyHome != null) {
                derbyLib = new File(derbyHome, "lib");
            }
            if (derbyLib == null || !derbyLib.exists()) {
                // maybe the jdk...
                if (System.getProperty("java.version").compareTo("1.6") > 0) {
                    File jdkHome = new File(System.getProperty("java.home"));
                    derbyLib = new File(jdkHome, "../db/lib");
                }
            }
            if (!derbyLib.exists()) {
                logger.info("Cannot find javadb client jar file, jdbc driver not available");
                return;
            }
            // Add all derby jars, as embedded driver is one jar and network driver
            // is in another.
            cpb.addGlob(derbyLib, "derby*.jar");
        }

        public ClassLoader build() {
            return cpb.create();
        }

        public void addLauncherJar() throws IOException {
            cpb.addJar(new File(glassfishDir, "modules/glassfish.jar"));
        }
    }

    static abstract class PlatformHelper {

        static synchronized PlatformHelper getPlatformHelper(Properties properties) {
            Constants.Platform platform =
                    Constants.Platform.valueOf(properties.getProperty(Constants.PLATFORM_PROPERTY_KEY));
            PlatformHelper platformHelper;
            switch (platform) {
                case Felix:
                    platformHelper = new FelixHelper();
                    break;
                case Knopflerfish:
                    platformHelper = new KnopflerfishHelper();
                    break;
                case Equinox:
                    platformHelper = new EquinoxHelper();
                    break;
                case Static:
                    platformHelper = new StaticHelper();
                    break;
                default:
                    throw new RuntimeException("Unsupported platform " + platform);
            }
            platformHelper.init(properties);
            return platformHelper;
        }

        protected Properties properties;
        protected File glassfishDir;
        protected File fwDir;

        /**
         * @param properties Initial properties
         */
        void init(Properties properties) {
            this.properties = properties;
            glassfishDir = StartupContextUtil.getInstallRoot(properties);
            setFwDir();
        }

        protected abstract void setFwDir();

        /**
         * Adds the jar files of the OSGi platform to the given {@link ClassPathBuilder}
         */
        protected abstract void addFrameworkJars(ClassPathBuilder cpb) throws IOException;

        /**
         * @return platform specific configuration information
         */
        protected Properties readPlatformConfiguration() throws IOException {
            Properties platformConfig = new Properties();
            final File configFile = getFrameworkConfigFile();
            if (configFile == null) return platformConfig;
            InputStream in = new FileInputStream(configFile);
            try {
                platformConfig.load(in);
            } finally {
                in.close();
            }
            return platformConfig;
        }

        protected abstract File getFrameworkConfigFile();
    }

    static class FelixHelper extends PlatformHelper {
        private static final String FELIX_HOME = "FELIX_HOME";

        /**
         * Home of FW installation relative to Glassfish root installation.
         */
        public static final String GF_FELIX_HOME = "osgi/felix";

        /**
         * Location of the config properties file relative to the fw installation
         */
        public static final String CONFIG_PROPERTIES = "conf/config.properties";

        @Override
        protected void setFwDir() {
            String fwPath = System.getenv(FELIX_HOME);
            if (fwPath == null) {
                // try system property, which comes from asenv.conf
                fwPath = System.getProperty(FELIX_HOME,
                        new File(glassfishDir, GF_FELIX_HOME).getAbsolutePath());
            }
            fwDir = new File(fwPath);
            if (!fwDir.exists()) {
                throw new RuntimeException("Can't locate Felix at " + fwPath);
            }
        }

        @Override
        protected File getFrameworkConfigFile() {
            /*
             * horrible shortcut to work around the issue that felix spends an infinite amount
             * of time resolve jaxb interfaces which are both part of the jdk and some distributions.
             *
             * When the distribution contains a repackaged jaxb, we are blocking the jdk API
             * visibility, otherwise we use the normal delegation model.
             *
             * This should be removed once the new felix resolver is integrated.
             */
            File jaxb = new File(glassfishDir, "modules/jaxb-osgi.jar");
            String fileName;
            if (jaxb.exists()) {
                fileName = CONFIG_PROPERTIES + ".nojaxb";
            } else {
                fileName = CONFIG_PROPERTIES;
            }
            return new File(fwDir, fileName);
        }

        @Override
        protected void addFrameworkJars(ClassPathBuilder cpb) throws IOException {
            cpb.addJar(new File(fwDir, "bin/felix.jar"));
        }
    }

    static class EquinoxHelper extends PlatformHelper {

        /* if equinox is installed under glassfish/eclipse this would be the
         *  glassfish/eclipse/plugins dir that contains the equinox jars
         *  can be null
         * */
        private static File pluginsDir = null;

        protected void setFwDir() {
            String fwPath = System.getenv("EQUINOX_HOME");
            if (fwPath == null) {
                fwPath = new File(glassfishDir, "osgi/equinox").getAbsolutePath();
            }
            fwDir = new File(fwPath);
            if (!fwDir.exists()) {
                fwDir = new File(glassfishDir, "osgi/eclipse");
            }
            if (fwDir.exists()) {//default Eclipse equinox structure from a equinoz zip distro
                pluginsDir = new File(fwDir, "plugins");
                if (!pluginsDir.exists()) {
                    pluginsDir = null;//no luck
                }
            }

            if (!fwDir.exists()) {
                throw new RuntimeException("Can't locate Equinox at " + fwPath);
            }
        }

        @Override
        protected File getFrameworkConfigFile() {
            return new File(fwDir, "configuration/config.ini");
        }

        @Override
        protected void addFrameworkJars(ClassPathBuilder cpb) throws IOException {
            // Add all the jars to classpath for the moment, since the jar name
            // is not a constant.
            if (pluginsDir != null) {
                cpb.addGlob(pluginsDir, "org.eclipse.osgi_*.jar");
            } else {
                cpb.addJarFolder(fwDir);
            }
        }
    }

    static class KnopflerfishHelper extends PlatformHelper {

        private static final String KF_HOME = "KNOPFLERFISH_HOME";

        /**
         * Home of fw installation relative to Glassfish root installation.
         */
        public static final String GF_KF_HOME = "osgi/knopflerfish.org/osgi/";

        /**
         * Location of the config properties file relative to the fw installation
         */
        public static final String CONFIG_PROPERTIES = "conf/config.properties";

        protected void setFwDir() {
            String fwPath = System.getenv(KF_HOME);
            if (fwPath == null) {
                fwPath = new File(glassfishDir, GF_KF_HOME).getAbsolutePath();
            }
            fwDir = new File(fwPath);
            if (!fwDir.exists()) {
                throw new RuntimeException("Can't locate KnopflerFish at " + fwPath);
            }
        }

        @Override
        protected void addFrameworkJars(ClassPathBuilder cpb) throws IOException {
            cpb.addJar(new File(fwDir, "framework.jar"));
        }

        @Override
        protected File getFrameworkConfigFile() {
            return new File(fwDir, CONFIG_PROPERTIES);
        }
    }

    static class StaticHelper extends PlatformHelper {
        @Override
        protected void setFwDir() {
            // nothing to do
        }

        @Override
        protected void addFrameworkJars(ClassPathBuilder cpb) throws IOException {
            // nothing to do
        }

        @Override
        protected File getFrameworkConfigFile() {
            return null;  // no config file for this platform.
        }
    }
}

