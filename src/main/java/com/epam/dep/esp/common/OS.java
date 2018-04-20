/*
 *  /***************************************************************************
 *  Copyright (c) 2017, EPAM SYSTEMS INC
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  ***************************************************************************
 */

package com.epam.dep.esp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum OS {
    win {
    }, linux {
    }, SunOs {
    },
    unknown {
    };

    protected final static Logger LOGGER = LoggerFactory.getLogger(OS.class);
    private final static int THREAD_TIME_OUT = 10000;
    public static final String arch = System.getProperty("os.arch").replaceFirst("amd64", "x64");
    public static final String osName = System.getProperty("os.name").toLowerCase();
    public static final String version = System.getProperty("os.version");
    public static final String patch = System.getProperty("sun.os.patch.level");
    public static final String INTERRUPTED = "Interrupted";

    OS() {

    }

    public static OS getOs() {
        LOGGER.debug(osName);
        if (osName.startsWith("windows")) return win;
        else if (osName.startsWith("linux")) return linux;
        else if (osName.startsWith("sunos")) return SunOs;
        else return unknown;
    }

    public Integer execCommandLine(List<String> command, List<String> out, String homeFolder, int timeout) {
        return execCommandLine(command, out, homeFolder, timeout, null);
    }

    public synchronized Integer execCommandLine(List<String> command, List<String> out, String homeFolder, int timeout, Map<String, String> envVars) {
        Integer result = null;
        if (command != null && !command.isEmpty() && out != null) {
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Start command" + command);
                }
                List<String> processOut;
                ProcessBuilder builder = new ProcessBuilder(command);
                // put additional environment variables if needed
                if (envVars != null) {
                    builder.environment().putAll(envVars);
                }
                if (homeFolder != null) builder.directory(new File(homeFolder));
                final Process process = builder.start();
                Worker worker = new Worker(process);
                worker.start();

                try {
                    worker.join(timeout * 1000l);
                    if (worker.exit != null) {
                        result = worker.exit;
                    } else {
                        LOGGER.debug("Worker timeout");
                        worker.interrupt();
                        worker.join();
                    }
                } catch (InterruptedException e) {
                    LOGGER.error(INTERRUPTED, e);
                    worker.interrupt();
                } finally {
                    process.destroy();
                }
                LOGGER.debug("Pumpers finished.");
                processOut = worker.getOut();
                if (LOGGER.isDebugEnabled()) {
                    for (String item : processOut) {
                        LOGGER.debug(item);
                    }
                    LOGGER.debug("Exit code:" + result + " " + command + "\n\r");
                }
                out.addAll(processOut);

            } catch (IOException e) {
                LOGGER.error("IOError", e);
            }
        }
        return result;
    }

    private static class Worker extends Thread {
        private final Process process;
        List<String> result;
        private Integer exit;
        private final StreamPumper errorPumper;
        private final StreamPumper stdPumper;

        private Worker(Process process) {
            this.process = process;
            this.errorPumper = new StreamPumper(process.getErrorStream());
            this.stdPumper = new StreamPumper(process.getInputStream());
            result = new ArrayList<>();
            errorPumper.start();
            stdPumper.start();
        }

        @Override
        public void run() {
            boolean error = false;
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Wait for " + process.toString());
                }
                exit = process.waitFor();
            } catch (InterruptedException ignore) {
                LOGGER.error(INTERRUPTED, ignore);
                error = true;
            } finally {
                if (!error) {
                    try {
                        LOGGER.debug("Wait for error stream processing.");
                        errorPumper.join(THREAD_TIME_OUT);
                    } catch (InterruptedException e) {
                        LOGGER.error(INTERRUPTED, e);
                    }
                    try {
                        LOGGER.debug("Wait for std stream processing.");
                        stdPumper.join(THREAD_TIME_OUT);
                    } catch (InterruptedException e) {
                        LOGGER.error(INTERRUPTED, e);
                    }
                }

                if (errorPumper.isAlive()) {
                    errorPumper.interrupt();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Kill Error Pumper " + process.toString() + errorPumper.toString());
                    }
                }
                if (stdPumper.isAlive()) {
                    stdPumper.interrupt();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Kill Out Pumper " + process.toString() + stdPumper.toString());
                    }
                }

                if (error) {
                    try {
                        LOGGER.debug("Wait for error die.");
                        errorPumper.join(THREAD_TIME_OUT);
                    } catch (InterruptedException e) {
                        LOGGER.error(INTERRUPTED, e);
                    }
                    try {
                        LOGGER.debug("Wait for std die.");
                        stdPumper.join(THREAD_TIME_OUT);
                    } catch (InterruptedException e) {
                        LOGGER.error(INTERRUPTED, e);
                    }
                }
                result.addAll(errorPumper.getOut());
                result.addAll(stdPumper.getOut());
            }
        }

        public List<String> getOut() {
            return result;
        }

        private class StreamPumper extends Thread {
            private static final int SLEEP_TIME = 0;
            private final BufferedReader din;
            private boolean endOfStream = false;
            private final List<String> out = new ArrayList<>();

            public StreamPumper(InputStream is) {
                this.din = new BufferedReader(new InputStreamReader(is));
            }

            public List<String> getOut() {
                return out;
            }

            public void pumpStream() throws IOException {
                String line = din.readLine();
                if (line != null) {
                    out.add(line);
                    LOGGER.info(line);
                } else {
                    endOfStream = true;
                }
            }

            @Override
            public void run() {
                try {
                    try {
                        while (!endOfStream && !isInterrupted()) {
                            pumpStream();
                            sleep(SLEEP_TIME);
                        }
                    } catch (InterruptedException ie) {
                        LOGGER.error(INTERRUPTED, ie);
                    } finally {
                        din.close();
                    }
                } catch (IOException ioe) {
                    LOGGER.error("", ioe);
                }
            }
        }
    }
}