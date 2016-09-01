/**
 * Copyright (c) 2000-2016 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package com.liferay.faces.site.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;

import org.jsoup.Jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.liferay.faces.site.dto.Archetype;
import com.liferay.faces.site.dto.Suite;
import com.liferay.faces.util.logging.Logger;
import com.liferay.faces.util.logging.LoggerFactory;


/**
 * @author  Vernon Singleton
 * @author  Neil Griffin
 */
@ManagedBean(name = "archetypeService", eager = true)
@ApplicationScoped
public class ArchetypeServiceImpl implements ArchetypeService {

	// Logger
	private static final Logger logger = LoggerFactory.getLogger(ArchetypeServiceImpl.class);

	// Private Constants
	private static final String ARCHETYPE_GENERATE_COMMAND = "mvn archetype:generate \\<br />" +
		"  -DarchetypeGroupId=com.liferay.faces.archetype \\<br />" +
		"  -DarchetypeArtifactId=com.liferay.faces.archetype.SUITE.portlet \\<br />" +
		"  -DarchetypeVersion=VERSION \\<br />" + "  -DgroupId=com.mycompany \\<br />" +
		"  -DartifactId=com.mycompany.my.SUITE.portlet";

	// Private Data Members
	private List<Archetype> archetypes;
	private String archetypeContext = "https://repo1.maven.org/maven2/com/liferay/faces/archetype/";
	private List<String> jsfVersions;
	private List<String> liferayVersions;
	private boolean snapshot = false;
	private List<Suite> suites;

	public ArchetypeServiceImpl() {
		FacesContext startupFacesContext = FacesContext.getCurrentInstance();
		ExternalContext startupExternalContext = startupFacesContext.getExternalContext();
		@SuppressWarnings("unchecked")
		Map<String, String> initParameterMap = (Map<String, String>) startupExternalContext.getInitParameterMap();
		init(initParameterMap);
	}

	@Override
	public List<Archetype> getArchetypes() {
		return archetypes;
	}

	@Override
	public List<String> getJsfVersions() {
		return jsfVersions;
	}

	@Override
	public List<String> getLiferayVersions() {
		return liferayVersions;
	}

	@Override
	public List<Suite> getSuites() {
		return suites;
	}

	@Override
	public void init(Map<String, String> contextParameterMap) {

		ArrayList<String> namesList = new ArrayList<String>();
		HashMap<String, String> extVersionsMap = new HashMap<String, String>();
		HashMap<String, String> liferayVersionsMap = new HashMap<String, String>();
		HashMap<String, String> jsfVersionsMap = new HashMap<String, String>();
		HashMap<String, String> extLiferayMap = new HashMap<String, String>();
		HashMap<String, String> extJsfMap = new HashMap<String, String>();

		Set<Map.Entry<String, String>> entrySet = contextParameterMap.entrySet();

		for (Map.Entry<String, String> mapEntry : entrySet) {

			String key = mapEntry.getKey();

			if (key.matches("snapshot")) {

				if ("true".equals(mapEntry.getValue())) {
					snapshot = true;
				}
			}

			if (key.matches("liferay-\\d+..*")) {

				namesList.add(key);

				String[] versions = key.split(" ");
				String extVersion = mapEntry.getValue();
				String liferayVersion = versions[0].substring("liferay-".length());
				String jsfVersion = versions[1];
				String qualifiedExt = extVersion + ((snapshot) ? "-SNAPSHOT" : "");

				extVersionsMap.put(qualifiedExt, "1");
				extLiferayMap.put(qualifiedExt, liferayVersion);
				extJsfMap.put(qualifiedExt, jsfVersion);
				liferayVersionsMap.put(liferayVersion, "1");
				jsfVersionsMap.put(jsfVersion, "1");
			}
		}

		String[] names = namesList.toArray(new String[namesList.size()]);
		Arrays.sort(names, Collections.reverseOrder());

		if (liferayVersions == null) {

			liferayVersions = new ArrayList<String>();

			String[] liferays = liferayVersionsMap.keySet().toArray(new String[liferayVersionsMap.keySet().size()]);
			Arrays.sort(liferays, Collections.reverseOrder());
			Collections.addAll(liferayVersions, liferays);
		}

		if (jsfVersions == null) {

			jsfVersions = new ArrayList<String>();

			String[] jsfs = jsfVersionsMap.keySet().toArray(new String[jsfVersionsMap.keySet().size()]);
			Arrays.sort(jsfs, Collections.reverseOrder());
			Collections.addAll(jsfVersions, jsfs);
		}

		HashMap<String, String> suiteMap = new HashMap<String, String>();
		suites = new ArrayList<Suite>();

		archetypes = new ArrayList<Archetype>();

		String nextUrl;
		String groupId = "com.liferay.faces.archetype";

		if (snapshot) {
			archetypeContext = "https://oss.sonatype.org/content/repositories/snapshots/com/liferay/faces/archetype/";
		}

		logger.debug("init: using archetypeContext = " + archetypeContext);

		Document suitesDocument;

		try {
			suitesDocument = Jsoup.connect(archetypeContext).get();

			for (Element potentialSuite : suitesDocument.select("td,pre").select("a")) {

				if (potentialSuite.attr("href").contains(groupId)) {

					String suite;

					if (potentialSuite.attr("href").contains(archetypeContext)) {
						suite = potentialSuite.attr("href").substring(archetypeContext.length()).replaceAll("/", "")
							.substring("com.liferay.faces.archetype.".length()).replace(".portlet", "");
						nextUrl = potentialSuite.attr("href");
					}
					else {
						suite = potentialSuite.attr("href").replaceAll("/", "").substring("com.liferay.faces.archetype."
								.length()).replace(".portlet", "");
						nextUrl = archetypeContext + potentialSuite.attr("href");
					}

					suiteMap.put(suite, "1");

					Document versionsDocument = Jsoup.connect(nextUrl).get();

					for (Element potentialVersion : versionsDocument.select("td,pre").select("a")) {

						String extVersion;

						if (potentialVersion.attr("href").contains(potentialSuite.attr("href"))) {
							extVersion = potentialVersion.attr("href").substring(potentialSuite.attr("href").length())
								.replaceAll("/", "");
						}
						else {
							extVersion = potentialVersion.attr("href").replaceAll("/", "");
						}

						// check this potentialVersion against the exts in the init-params ...
						if (extVersionsMap.containsKey(extVersion)) {
							logger.debug("init: extVersion = " + extVersion);

							String jsfVersion = extJsfMap.get(extVersion);
							String liferayVersion = extLiferayMap.get(extVersion);
							String mavenCommand = ARCHETYPE_GENERATE_COMMAND.replace("VERSION", extVersion).replaceAll(
									"SUITE", suite);

							if (potentialVersion.attr("href").contains(potentialSuite.attr("href"))) {
								nextUrl = potentialVersion.attr("href");
							}
							else {
								nextUrl = archetypeContext + potentialSuite.attr("href") +
									potentialVersion.attr("href");
							}

							Document filesDocument = Jsoup.connect(nextUrl).get();

							String fileName = "";
							URL url = null;

							for (Element potentialFile : filesDocument.select("td,pre").select("a")) {

								if (potentialFile.attr("href").matches("..*.jar")) {

									if (potentialFile.attr("href").contains(potentialVersion.attr("href"))) {
										fileName = potentialFile.attr("href").substring(potentialVersion.attr("href")
												.length()).replaceAll("/", "");
										url = new URL(potentialFile.attr("href"));
									}
									else {
										fileName = potentialFile.attr("href").replaceAll("/", "");
										url = new URL(archetypeContext + potentialSuite.attr("href") +
												potentialVersion.attr("href") + potentialFile.attr("href"));
									}
								}
							}

							// get the model, properties, and dependencies for the archetype
							if (url != null) {

								try {

									HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
									urlConnection.setRequestMethod("GET");
									urlConnection.setDoOutput(true);
									urlConnection.connect();

									File file = new File(fileName);

									FileOutputStream fileOutput = new FileOutputStream(file);
									InputStream inputStream = urlConnection.getInputStream();

									byte[] buffer = new byte[1024];
									int bufferLength;

									while ((bufferLength = inputStream.read(buffer)) > 0) {
										fileOutput.write(buffer, 0, bufferLength);
									}

									fileOutput.close();

									JarFile jar = new JarFile(file);
									Enumeration<JarEntry> entries = jar.entries();

									while (entries.hasMoreElements()) {
										JarEntry entry = entries.nextElement();

										if ("archetype-resources/pom.xml".equals(entry.getName())) {

											File pom = new File("pom.xml");

											InputStream is = jar.getInputStream(entry);
											FileOutputStream fos = new FileOutputStream(pom);

											while (is.available() > 0) {
												fos.write(is.read());
											}

											fos.close();
											is.close();

											// MavenXpp3Reader reader = new MavenXpp3Reader();
											// Model model = null;
											String dependencyLines = "";

											try {
												Map<String, String> propertyMap = new HashMap<String, String>();
												// model = reader.read(new FileReader(pom));

												List<String> lines = Files.readAllLines(Paths.get(
															pom.getCanonicalPath()), StandardCharsets.UTF_8);
												boolean inProperties = false;
												boolean inDependencies = false;
												boolean inDependencyManagement = false;
												boolean startTag;

												for (String line : lines) {

													startTag = false;

													if (line.contains("<properties>")) {
														inProperties = true;
														startTag = true;
													}
													else if (line.contains("</properties>")) {
														inProperties = false;
													}
													else if (line.contains("<dependencies>")) {
														inDependencies = true;
														startTag = true;
													}
													else if (line.contains("<dependencyManagement>")) {
														inDependencyManagement = true;
														startTag = true;
													}

													if (inProperties && !startTag) {

														String propertyName = null;
														String propertyValue = null;
														String[] tokens = line.trim().split("[<>/]");

														for (String token : tokens) {

															if ((token != null) && (token.length() > 0)) {

																if (propertyName == null) {
																	propertyName = token;
																}
																else if (propertyValue == null) {
																	propertyValue = token;
																}
															}
														}

														propertyMap.put(propertyName, propertyValue);
													}

													if (inDependencies) {

														int startExpressionPos = line.indexOf("${");

														if (startExpressionPos > 0) {
															int finishExpresionPos = line.indexOf("}",
																	startExpressionPos);

															if (finishExpresionPos > 0) {
																String propertyExpression = line.substring(
																		startExpressionPos + 2, finishExpresionPos);
																String propertyExpressionValue = propertyMap.get(
																		propertyExpression);

																if (propertyExpressionValue != null) {
																	line = line.substring(0, startExpressionPos) +
																		propertyExpressionValue +
																		line.substring(finishExpresionPos + 1);
																}
															}
														}
													}

													if (inDependencies || inDependencyManagement) {

														line = line.replaceAll("^\\t", "");
														line = line.replaceAll("\\t", "    ");
														dependencyLines += line;
														dependencyLines += "\n";
													}

													if (line.contains("</dependencies>")) {
														inDependencies = false;
													}
													else if (line.contains("</dependencyManagement>")) {
														inDependencyManagement = false;
													}
												}
											}
											catch (Exception e) {
												logger.error(e);
											}

											logger.debug(
												"init: liferayVersion=[{0}] jsfVersion=[{1}] suite=[{2}] extVersion=[{3}]",
												liferayVersion, jsfVersion, suite, extVersion);
											archetypes.add(new Archetype(liferayVersion, jsfVersion, suite,
													dependencyLines, mavenCommand));
										}
									}

									jar.close();

									boolean successfullyDeleted = file.delete();

									if (!successfullyDeleted) {
										logger.error("unable to delete file {0}", file.toString());
									}
								}
								catch (Exception e) {
									logger.error(e);
								}
							}
							else {
								logger.error("unable to determine url");
							}
						}
					}
				}
			}
		}
		catch (IOException e1) {
			e1.printStackTrace();
		}

		for (Map.Entry<String, String> entry : suiteMap.entrySet()) {
			String suiteName = entry.getKey();
			suites.add(new Suite(suiteName, getSuiteTitle(suiteName)));
		}
	}

	private String getSuiteTitle(String suiteName) {

		if ("alloy".equals(suiteName)) {
			return "Liferay Faces Alloy";
		}
		else if ("bootsfaces".equals(suiteName)) {
			return "BootsFaces";
		}
		else if ("butterfaces".equals(suiteName)) {
			return "ButterFaces";
		}
		else if ("icefaces".equals(suiteName)) {
			return "ICEfaces";
		}
		else if ("jsf".equals(suiteName)) {
			return "JSF Standard";
		}
		else if ("metal".equals(suiteName)) {
			return "Liferay Faces Metal";
		}
		else if ("primefaces".equals(suiteName)) {
			return "PrimeFaces";
		}
		else if ("richfaces".equals(suiteName)) {
			return "RichFaces";
		}
		else {
			return null;
		}
	}
}
