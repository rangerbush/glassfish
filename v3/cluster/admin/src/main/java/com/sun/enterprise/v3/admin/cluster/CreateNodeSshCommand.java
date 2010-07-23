/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.enterprise.v3.admin.cluster;

import com.sun.enterprise.util.StringUtils;
import com.sun.enterprise.util.net.NetUtils;
import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.*;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.api.admin.CommandRunner.CommandInvocation;
import org.jvnet.hk2.annotations.*;
import org.jvnet.hk2.component.*;
import com.sun.enterprise.universal.glassfish.TokenResolver;
import org.glassfish.cluster.ssh.launcher.SSHLauncher;
import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.HashMap;

/**
 * Remote AdminCommand to create and ssh node.  This command is run only on DAS.
 * Register the node with SSH info on DAS
 *
 * @author Carla Mott
 */
@Service(name = "create-node-ssh")
@I18n("create.node.ssh")
@Scoped(PerLookup.class)
@Cluster({RuntimeType.DAS})
public class CreateNodeSshCommand implements AdminCommand  {

    @Inject
    private CommandRunner cr;

    @Inject
    Habitat habitat;

    @Param(name="name", primary = true)
    private String name;

    @Param(name="nodehost")
    private String nodehost;

    @Param(name = "installdir", optional=true)
    private String installdir;

    @Param(name="nodedir", optional=true)
    private String nodedir;

    @Param(name="sshport", optional=true)
    private String sshport;

    @Param(name = "sshuser", optional = true)
    private String sshuser;

    @Param(name = "sshkeyfile", optional = true)
    private String sshkeyfile;

    @Param(name = "force", optional = true, defaultValue = "false")
    private boolean force;

    private static final String NL = System.getProperty("line.separator");

    private Logger logger = null;
    private TokenResolver resolver = null;

    @Override
    public void execute(AdminCommandContext context) {
        ActionReport report = context.getActionReport();
        StringBuilder msg = new StringBuilder();

        logger = context.getLogger();

        // Create a resolver that can replace system properties in strings
        Map<String, String> systemPropsMap =
                new HashMap<String, String>((Map)(System.getProperties()));
        resolver = new TokenResolver(systemPropsMap);

        setDefaults();
        try {
            validate();
        } catch (CommandValidationException e) {
            String m1 = Strings.get("create.node.ssh.invalid.params");
            if (!force) {
                String m2 = Strings.get("create.node.ssh.not.created");
                msg.append(StringUtils.cat(NL, m1, m2, e.getMessage()));
                report.setMessage(msg.toString());
                report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                return;
            } else {
                String m2 = Strings.get("create.node.ssh.continue.force");
                msg.append(StringUtils.cat(NL, m1, e.getMessage(), m2));
            }
        }

        CommandInvocation ci = cr.getCommandInvocation("_create-node", report);
        ParameterMap map = new ParameterMap();
        map.add("DEFAULT", name);
        map.add("installdir", installdir);
        map.add("nodehost", nodehost);
        map.add("nodedir", nodedir);
        map.add("sshport", sshport);
        map.add("sshuser", sshuser);
        map.add("sshkeyfile", sshkeyfile);
        ci.parameters(map);
        ci.execute();

        if (StringUtils.ok(report.getMessage())) {
            if (msg.length() > 0) {
                msg.append(NL);
            }
            msg.append(report.getMessage());
        }

        report.setMessage(msg.toString());
    }

    private void setDefaults() {
        if (sshport == null) {
            sshport = "22";
        }
        if (sshuser == null) {
            sshuser = "${user.name}";
        }
        if (installdir == null) {
            installdir = "${com.sun.aas.installRoot}";
        }
    }

    private void validate() throws CommandValidationException {

        if (StringUtils.ok(sshkeyfile)) {
            // User specified a key file. Make sure we get use it
            File kfile = new File(resolver.resolve(sshkeyfile));
            if (! kfile.isAbsolute()) {
                throw new CommandValidationException(
                        Strings.get("key.path.not.absolute",
                        kfile.getPath()));
            }
            if (! kfile.exists()) {
                throw new CommandValidationException(
                        Strings.get("key.path.not.found",
                        kfile.getPath()));
            }
            if (! kfile.canRead() ) {
                throw new CommandValidationException(
                        Strings.get("key.path.not.readable",
                        kfile.getPath(), System.getProperty("user.name")) );
            }
        }

        validateSSHConnection();
    }

    private void validateSSHConnection() throws CommandValidationException {
        SSHLauncher sshL=habitat.getComponent(SSHLauncher.class);

        // We use the resolver to expand any system properties
        if (! NetUtils.isPortStringValid(resolver.resolve(sshport))) {
            throw new CommandValidationException(Strings.get(
                    "Invalid port number {0}", sshport));
        }

        int port = Integer.parseInt(resolver.resolve(sshport));

        try {
            sshL.validate(resolver.resolve(nodehost),
                          port,
                          resolver.resolve(sshuser),
                          resolver.resolve(sshkeyfile),
                          resolver.resolve(installdir),
                          logger);
        } catch (IOException e) {
            String m1 = e.getMessage();
            String m2 = "";
            Throwable e2 = e.getCause();
            if (e2 != null) {
                m2 = e2.getMessage();
            }
            if (e instanceof FileNotFoundException) {
                logger.warning(StringUtils.cat(": ", m1, m2, sshL.toString()));
                throw new CommandValidationException(StringUtils.cat(NL,
                                            m1, m2));
            } else {
                String msg = Strings.get("ssh.bad.connect", nodehost);
                logger.warning(StringUtils.cat(": ", msg, m1, m2,
                                            sshL.toString()));
                throw new CommandValidationException(StringUtils.cat(NL,
                                            msg, m1, m2));
            }
        }
    }
}