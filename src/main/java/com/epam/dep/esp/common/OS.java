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

public enum OS {
    win {
    }, linux {
    }, SunOs {
    },
    unknown {
    };

    protected final static Logger logger = LoggerFactory.getLogger(OS.class);
    private final static int THREAD_TIME_OUT = 10000;
    private final static int PROCESS_TIME_OUT = 120000;
    public static String arch = System.getProperty("os.arch").replaceFirst("amd64", "x64");
    public static String name = System.getProperty("os.name").toLowerCase();
    public static String version = System.getProperty("os.version");
    public static String patch = System.getProperty("sun.os.patch.level");

    OS() {

    }

    public static OS getOs() {
        logger.debug(name);
        if (name.startsWith("windows")) return win;
        else if (name.startsWith("linux")) return linux;
        else if (name.startsWith("sunos")) return SunOs;
        else return unknown;
    }

    public synchronized Integer execCommandLine(List<String> command, List<String> out, String homeFolder, int timeout) {
        Integer result = null;
        if (command != null && command.size() > 0 && out != null) {
            try {
                logger.debug("Start command" + command);
                List<String> processOut;
                ProcessBuilder builder = new ProcessBuilder(command);
                if (homeFolder != null) builder.directory(new File(homeFolder));
                final Process process = builder.start();
                Worker worker = new Worker(process);
                worker.start();

                try {
                    //worker.join(PROCESS_TIME_OUT);
                    worker.join(timeout * 1000);
                    if (worker.exit != null) {
                        result = worker.exit;
                    } else {
                        logger.debug("Worker timeout");
                        worker.interrupt();
                        worker.join();
                    }
                } catch (InterruptedException e) {
                    logger.error("Interrupted", e);
                    worker.interrupt();
                } finally {
                    process.destroy();
                }
                logger.debug("Pumpers finished.");
                processOut = worker.getOut();
                if (logger.isDebugEnabled()) {
                    for (String item : processOut) {
                        logger.debug(item);
                    }
                    logger.debug("Exit code:" + result + " " + command + "\n\r");
                }
                out.addAll(processOut);

            } catch (IOException e) {
                logger.error("IOError", e);
            }
        }
        return result;
    }

    private static class Worker extends Thread {
        private final Process process;
        List<String> result;
        private Integer exit;
        private StreamPumper errorPumper;
        private StreamPumper stdPumper;

        private Worker(Process process) {
            this.process = process;
            this.errorPumper = new StreamPumper(process.getErrorStream());
            this.stdPumper = new StreamPumper(process.getInputStream());
            result = new ArrayList<>();
            errorPumper.start();
            stdPumper.start();
        }

        public void run() {
            boolean error = false;
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Wait for " + process.toString());
                }
                exit = process.waitFor();
            } catch (InterruptedException ignore) {
                logger.error("Interrupted", ignore);
                error = true;
            } finally {
                if (!error) {
                    try {
                        logger.debug("Wait for error stream processing.");
                        errorPumper.join(THREAD_TIME_OUT);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted", e);
                    }
                    try {
                        logger.debug("Wait for std stream processing.");
                        stdPumper.join(THREAD_TIME_OUT);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted", e);
                    }
                }

                if (errorPumper.isAlive()) {
                    errorPumper.interrupt();
                    if (logger.isDebugEnabled()) {
                        logger.debug("Kill Error Pumper " + process.toString() + errorPumper.toString());
                    }
                }
                if (stdPumper.isAlive()) {
                    stdPumper.interrupt();
                    if (logger.isDebugEnabled()) {
                        logger.debug("Kill Out Pumper " + process.toString() + stdPumper.toString());
                    }
                }

                if (error) {
                    try {
                        logger.debug("Wait for error die.");
                        errorPumper.join(THREAD_TIME_OUT);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted", e);
                    }
                    try {
                        logger.debug("Wait for std die.");
                        stdPumper.join(THREAD_TIME_OUT);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted", e);
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
            private List<String> out = new ArrayList<String>();

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
                } else {
                    endOfStream = true;
                }
            }

            public void run() {
                try {
                    try {
                        while (!endOfStream && !isInterrupted()) {
                            pumpStream();
                            sleep(SLEEP_TIME);
                        }
                    } catch (InterruptedException ie) {
                        logger.error("Interrupted", ie);
                    } finally {
                        din.close();
                    }
                } catch (IOException ioe) {
                    logger.error("", ioe);
                }
            }
        }
    }
}