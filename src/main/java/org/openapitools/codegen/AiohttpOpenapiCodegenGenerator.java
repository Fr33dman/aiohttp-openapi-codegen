package org.openapitools.codegen;

import static org.openapitools.codegen.utils.StringUtils.underscore;

import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.AbstractPythonCodegen;

public class AiohttpOpenapiCodegenGenerator extends AbstractPythonCodegen {

  public static final String OPTION_PYTHON_SRC_ROOT = "pythonSrcRoot";
  public static final String OPTION_APP_NAME = "appName";
  public static final String OPTION_APP_DESCRIPTION = "appDescription";
  public static final String OPTION_INFO_EMAIL = "infoEmail";
  public static final String OPTION_PACKAGE_URL = "packageUrl";
  public static final String OPTION_SERVER_PORT = "serverPort";
  public static final String OPTION_CONTEXT_PATH = "contextPath";
  public static final String OPTION_FEATURE_CORS = "featureCORS";
  public static final String OPTION_GENERATOR_LANGUAGE_VERSION = "generatorLanguageVersion";
  public static final String OPTION_TESTS_ROOT = "testsRoot";

  private String pythonSrcRoot = "";
  private String testsRoot = "tests";
  private String serverPort = "8080";
  private String contextPath = "";
  private boolean featureCORS = false;
  private String generatorLanguageVersion = "3.11";

  private String packageRootDir = "";
  private String controllersDir = "";
  private String schemasDir = "";
  private String testsDir = "";
  private String controllerTestsDir = "";

  public AiohttpOpenapiCodegenGenerator() {
    super();

    outputFolder = "generated-code/aiohttp-openapi-codegen";
    templateDir = "aiohttp-openapi-codegen";
    embeddedTemplateDir = templateDir;
    /*
     * Default folder names follow standard python layout.
     * testsRoot is tracked locally because AbstractPythonCodegen does not expose it.
     */
    modelTemplateFiles.clear();
    modelTemplateFiles.put("model.mustache", ".py");

    apiTemplateFiles.clear();
    apiTemplateFiles.put("controller.mustache", ".py");

    apiTestTemplateFiles.clear();
    apiTestTemplateFiles.put("controller_test.mustache", ".py");

    cliOptions.add(new CliOption(CodegenConstants.PACKAGE_NAME, "Root python package name")
      .defaultValue(this.packageName));
    cliOptions.add(new CliOption(CodegenConstants.PACKAGE_VERSION, "Package version")
      .defaultValue(this.packageVersion));
    cliOptions.add(new CliOption(OPTION_PYTHON_SRC_ROOT,
      "Relative path where generated python sources will be placed")
      .defaultValue(this.pythonSrcRoot));
    cliOptions.add(new CliOption(OPTION_APP_NAME, "Human readable application name"));
    cliOptions.add(new CliOption(OPTION_APP_DESCRIPTION, "Application description used in README/setup.py"));
    cliOptions.add(new CliOption(OPTION_INFO_EMAIL, "Contact email exposed in the setup metadata"));
    cliOptions.add(new CliOption(OPTION_PACKAGE_URL, "Project URL used in the setup metadata"));
    cliOptions.add(new CliOption(OPTION_SERVER_PORT, "Port exposed by the generated aiohttp server")
      .defaultValue(this.serverPort));
    cliOptions.add(new CliOption(OPTION_CONTEXT_PATH, "Context path prefix for the aiohttp routes"));
    cliOptions.add(new CliOption(OPTION_FEATURE_CORS, "Enable aiohttp_cors integration")
      .defaultValue(Boolean.toString(this.featureCORS)));
    cliOptions.add(new CliOption(OPTION_GENERATOR_LANGUAGE_VERSION,
      "Python version documented in README/metadata")
      .defaultValue(this.generatorLanguageVersion));
    cliOptions.add(new CliOption(OPTION_TESTS_ROOT, "Relative path where generated tests will be written")
      .defaultValue(this.testsRoot));
  }

  @Override
  public CodegenType getTag() {
    return CodegenType.SERVER;
  }

  @Override
  public String getName() {
    return "aiohttp-openapi-codegen";
  }

  @Override
  public String getHelp() {
    return "Generates a pure aiohttp server stub.";
  }

  @Override
  public String toApiFilename(String name) {
    return super.toApiFilename(name) + "_controller";
  }

  @Override
  public void processOpts() {
    super.processOpts();

    configurePackageName();
    configurePackageVersion();

    pythonSrcRoot = resolvePythonSrcRoot();
    testsRoot = resolveStringOpt(OPTION_TESTS_ROOT, testsRoot);
    additionalProperties.put(OPTION_PYTHON_SRC_ROOT, pythonSrcRoot);
    additionalProperties.put(OPTION_TESTS_ROOT, testsRoot);
    additionalProperties.put(CodegenConstants.SOURCE_FOLDER, pythonSrcRoot);

    featureCORS = resolveBooleanOpt(OPTION_FEATURE_CORS, featureCORS);
    additionalProperties.put(OPTION_FEATURE_CORS, featureCORS);

    serverPort = sanitizePort(resolveStringOpt(OPTION_SERVER_PORT, serverPort));
    contextPath = sanitizeContextPath(resolveStringOpt(OPTION_CONTEXT_PATH, contextPath));
    generatorLanguageVersion = resolveStringOpt(OPTION_GENERATOR_LANGUAGE_VERSION, generatorLanguageVersion);

    applyServerDefaultsIfAvailable();

    if (StringUtils.isBlank(serverPort)) {
      serverPort = "8080";
    }

    if (contextPath == null) {
      contextPath = "";
    }

    additionalProperties.put(OPTION_SERVER_PORT, serverPort);
    additionalProperties.put(OPTION_CONTEXT_PATH, contextPath);
    additionalProperties.put(OPTION_GENERATOR_LANGUAGE_VERSION, generatorLanguageVersion);

    configureInfoDefaults();

    determineLayout();
    supportingFiles.clear();
    supportingFiles.add(new SupportingFile("app.mustache", packageRootDir, "app.py"));
    supportingFiles.add(new SupportingFile("__init__main.mustache", packageRootDir, "__init__.py"));
    supportingFiles.add(new SupportingFile("typing_utils.mustache", packageRootDir, "typing_utils.py"));
    supportingFiles.add(new SupportingFile("util.mustache", packageRootDir, "util.py"));
    supportingFiles.add(new SupportingFile("__init__.mustache", controllersDir, "__init__.py"));
    supportingFiles.add(new SupportingFile("base_model.mustache", schemasDir, "base_model.py"));
    supportingFiles.add(new SupportingFile("__init__model.mustache", schemasDir, "__init__.py"));
    supportingFiles.add(new SupportingFile("conftest.mustache", testsDir, "conftest.py"));
    supportingFiles.add(new SupportingFile("__init__test.mustache", testsDir, "__init__.py"));
  }

  private void configurePackageName() {
    String candidate = additionalProperties.containsKey(CodegenConstants.PACKAGE_NAME)
      ? additionalProperties.get(CodegenConstants.PACKAGE_NAME).toString()
      : this.packageName;
    String sanitized = sanitizePackageNameValue(candidate);
    setPackageName(sanitized);
    setProjectName(sanitized);
    additionalProperties.put(CodegenConstants.PACKAGE_NAME, sanitized);

    if (additionalProperties.containsKey(CodegenConstants.API_PACKAGE)) {
      setApiPackage(additionalProperties.get(CodegenConstants.API_PACKAGE).toString());
    } else {
      setApiPackage(sanitized + ".controllers");
      additionalProperties.put(CodegenConstants.API_PACKAGE, apiPackage);
    }

    if (additionalProperties.containsKey(CodegenConstants.MODEL_PACKAGE)) {
      setModelPackage(additionalProperties.get(CodegenConstants.MODEL_PACKAGE).toString());
    } else {
      setModelPackage(sanitized + ".schemas");
      additionalProperties.put(CodegenConstants.MODEL_PACKAGE, modelPackage);
    }

    additionalProperties.putIfAbsent(CodegenConstants.INVOKER_PACKAGE, sanitized);
  }

  private void configurePackageVersion() {
    String resolved = additionalProperties.containsKey(CodegenConstants.PACKAGE_VERSION)
      ? additionalProperties.get(CodegenConstants.PACKAGE_VERSION).toString()
      : deriveVersionFromSpec().orElse(this.packageVersion);
    if (StringUtils.isBlank(resolved)) {
      resolved = this.packageVersion;
    }
    setPackageVersion(resolved);
    additionalProperties.put(CodegenConstants.PACKAGE_VERSION, this.packageVersion);
  }

  private Optional<String> deriveVersionFromSpec() {
    if (openAPI == null || openAPI.getInfo() == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(openAPI.getInfo().getVersion())
      .filter(StringUtils::isNotBlank);
  }

  private void configureInfoDefaults() {
    Info info = openAPI != null ? openAPI.getInfo() : null;
    Contact contact = info != null ? info.getContact() : null;

    String defaultAppName = info != null && StringUtils.isNotBlank(info.getTitle())
      ? info.getTitle()
      : StringUtils.capitalize(packageName.replace('_', ' '));
    String defaultDescription = info != null && StringUtils.isNotBlank(info.getDescription())
      ? info.getDescription()
      : String.format("%s server stub generated by OpenAPI Generator.", defaultAppName);
    String defaultEmail = contact != null && StringUtils.isNotBlank(contact.getEmail())
      ? contact.getEmail()
      : "support@example.com";
    String defaultUrl;
    if (contact != null && StringUtils.isNotBlank(contact.getUrl())) {
      defaultUrl = contact.getUrl();
    } else if (info != null && StringUtils.isNotBlank(info.getTermsOfService())) {
      defaultUrl = info.getTermsOfService();
    } else {
      defaultUrl = "https://example.com";
    }

    additionalProperties.put(OPTION_APP_NAME, resolveStringOpt(OPTION_APP_NAME, defaultAppName));
    additionalProperties.put(OPTION_APP_DESCRIPTION, resolveStringOpt(OPTION_APP_DESCRIPTION, defaultDescription));
    additionalProperties.put(OPTION_INFO_EMAIL, resolveStringOpt(OPTION_INFO_EMAIL, defaultEmail));
    additionalProperties.put(OPTION_PACKAGE_URL, resolveStringOpt(OPTION_PACKAGE_URL, defaultUrl));
  }

  private String resolvePythonSrcRoot() {
    String value = resolveStringOpt(OPTION_PYTHON_SRC_ROOT, pythonSrcRoot);
    if (".".equals(value)) {
      value = "";
    }
    return value;
  }

  private String resolveStringOpt(String key, String defaultValue) {
    Object value = additionalProperties.get(key);
    if (value == null) {
      additionalProperties.put(key, defaultValue);
      return defaultValue;
    }
    String str = value.toString().trim();
    if (str.isEmpty()) {
      additionalProperties.put(key, defaultValue);
      return defaultValue;
    }
    return str;
  }

  private boolean resolveBooleanOpt(String key, boolean defaultValue) {
    Object value = additionalProperties.get(key);
    if (value == null) {
      additionalProperties.put(key, defaultValue);
      return defaultValue;
    }
    boolean parsed = value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(value.toString());
    additionalProperties.put(key, parsed);
    return parsed;
  }

  private String sanitizePackageNameValue(String value) {
    String candidate = StringUtils.defaultString(value, "").trim();
    if (".".equals(candidate)) {
      candidate = new File(outputFolder).getName();
    }
    List<String> tokens = Arrays.stream(candidate.split("\\."))
      .map(String::trim)
      .filter(StringUtils::isNotBlank)
      .map(this::sanitizePackageToken)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.toList());
    if (tokens.isEmpty()) {
      tokens.add("aiohttp_server");
    }
    return String.join(".", tokens);
  }

  private String sanitizePackageToken(String rawSegment) {
    String candidate = rawSegment.replace('-', '_').replace(' ', '_');
    String snake = underscore(candidate).replaceAll("[^a-z0-9_]", "");
    if (StringUtils.isBlank(snake)) {
      snake = "pkg";
    }
    if (Character.isDigit(snake.charAt(0))) {
      snake = "_" + snake;
    }
    return snake;
  }

  private void applyServerDefaultsIfAvailable() {
    if (openAPI == null || openAPI.getServers() == null) {
      return;
    }

    for (Server server : openAPI.getServers()) {
      Optional<URI> uri = resolveServerUri(server);
      if (!uri.isPresent()) {
        continue;
      }
      URI resolved = uri.get();
      if (StringUtils.isBlank(serverPort)) {
        int port = resolved.getPort();
        if (port <= 0 && StringUtils.isNotBlank(resolved.getScheme())) {
          if ("https".equalsIgnoreCase(resolved.getScheme())) {
            port = 443;
          } else if ("http".equalsIgnoreCase(resolved.getScheme())) {
            port = 80;
          }
        }
        if (port > 0) {
          serverPort = String.valueOf(port);
        }
      }
      if (StringUtils.isBlank(contextPath) && StringUtils.isNotBlank(resolved.getPath())) {
        contextPath = sanitizeContextPath(resolved.getPath());
      }

      if (StringUtils.isNotBlank(serverPort) && StringUtils.isNotBlank(contextPath)) {
        break;
      }
    }
  }

  private Optional<URI> resolveServerUri(Server server) {
    if (server == null || StringUtils.isBlank(server.getUrl())) {
      return Optional.empty();
    }
    String resolved = server.getUrl();
    if (server.getVariables() != null) {
      for (Map.Entry<String, ServerVariable> entry : server.getVariables().entrySet()) {
        String placeholder = "{" + entry.getKey() + "}";
        String replacement = entry.getValue() != null && StringUtils.isNotBlank(entry.getValue().getDefault())
          ? entry.getValue().getDefault()
          : entry.getKey();
        resolved = resolved.replace(placeholder, replacement);
      }
    }
    try {
      return Optional.of(new URI(resolved));
    } catch (URISyntaxException ex) {
      return Optional.empty();
    }
  }

  private String sanitizePort(String candidate) {
    if (StringUtils.isBlank(candidate)) {
      return "";
    }
    String trimmed = candidate.trim();
    return trimmed.matches("\\d+") ? trimmed : "";
  }

  private String sanitizeContextPath(String candidate) {
    if (StringUtils.isBlank(candidate) || "/".equals(candidate.trim())) {
      return "";
    }
    String result = candidate.trim();
    if (!result.startsWith("/")) {
      result = "/" + result;
    }
    while (result.endsWith("/") && result.length() > 1) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  private String joinPath(String... parts) {
    return Arrays.stream(parts)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.joining(File.separator));
  }

  private void determineLayout() {
    final String sanitizedPackageDir = packageName.replace('.', File.separatorChar);
    final String lastSegment = packageName.contains(".")
      ? packageName.substring(packageName.lastIndexOf('.') + 1)
      : packageName;
    final String outputDirName = new File(outputFolder).getName();
    final boolean hasNamespace = packageName.contains(".");
    final boolean flatten = !hasNamespace && outputDirName.equals(lastSegment);

    if (flatten) {
      packageRootDir = "";
    } else {
      packageRootDir = joinPath(normalizeRelativePath(pythonSrcRoot), sanitizedPackageDir);
    }

    schemasDir = joinPath(packageRootDir, "schemas");
    controllersDir = joinPath(packageRootDir, "controllers");
    testsDir = joinPath(packageRootDir, normalizeRelativePath(testsRoot));
    controllerTestsDir = joinPath(testsDir, "controllers");
  }

  private String normalizeRelativePath(String value) {
    if (StringUtils.isBlank(value) || ".".equals(value)) {
      return "";
    }
    return value;
  }

  private String toOutputPath(String relative) {
    if (StringUtils.isBlank(relative)) {
      return outputFolder;
    }
    return outputFolder + File.separator + relative;
  }

  @Override
  public String modelFileFolder() {
    return toOutputPath(schemasDir);
  }

  @Override
  public String apiFileFolder() {
    return toOutputPath(controllersDir);
  }

  @Override
  public String apiTestFileFolder() {
    return toOutputPath(controllerTestsDir);
  }
}
