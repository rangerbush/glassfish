/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2009 Sun Microsystems, Inc. All rights reserved.
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

package org.glassfish.appclient.client.acc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author tjquinn
 */
public class ACCClassLoader extends URLClassLoader {

    private static ACCClassLoader instance = null;

    private ACCClassLoader shadow = null;

    private boolean shouldTransform = false;
    
    private final List<ClassFileTransformer> transformers =
            Collections.synchronizedList(
                new ArrayList<ClassFileTransformer>());

    public static ACCClassLoader newInstance(ClassLoader parent,
            final boolean shouldTransform) {
        if (instance != null) {
            throw new IllegalStateException("already set");
        }
        instance = new ACCClassLoader(parent, shouldTransform);
        return instance;
    }

    public static ACCClassLoader instance() {
        return instance;
    }

    public ACCClassLoader(ClassLoader parent, final boolean shouldTransform) {
        super(new URL[0], parent);
        this.shouldTransform = shouldTransform;
    }
    
    public ACCClassLoader(URL[] urls) {
        super(urls);
    }

    public ACCClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public ACCClassLoader(URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
        super(urls, parent, factory);
    }

    public synchronized void appendURL(final URL url) {
        addURL(url);
        if (shadow != null) {
            shadow.addURL(url);
        }
    }

    public void addTransformer(final ClassFileTransformer xf) {
        transformers.add(xf);
    }

    synchronized ACCClassLoader shadow() {
        if (shadow == null) {
            shadow = new ACCClassLoader( getURLs(), getParent());
            }
        return shadow;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if ( ! shouldTransform) {
            return super.findClass(name);
        }
        final ACCClassLoader s = shadow();
        final Class<?> c = s.findClassUnshadowed(name);
        return copyClass(c);
    }

    private Class<?> copyClass(final Class c) throws ClassNotFoundException {
        final String name = c.getName();
        final ProtectionDomain pd = c.getProtectionDomain();
        byte[] bytecode = readByteCode(name);

        for (ClassFileTransformer xf : transformers) {
            try {
                bytecode = xf.transform(this, name, null, pd, bytecode);
            } catch (IllegalClassFormatException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
        return defineClass(name, bytecode, 0, bytecode.length, pd);
    }

    private Class<?> findClassUnshadowed(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }

    private byte[] readByteCode(final String className) throws ClassNotFoundException {
        final String resourceName = className.replace('.', '/') + ".class";
        InputStream is = getResourceAsStream(resourceName);
        if (is == null) {
            throw new ClassNotFoundException(className);
        }
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8196];
            int bytesRead;
            while ( (bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new ClassNotFoundException(className, e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                throw new ClassNotFoundException(className, e);
            }
        }
    }
}
