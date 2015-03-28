package com.atsebak.raspberrypi.runner;

import com.atsebak.raspberrypi.localization.PIBundle;
import com.atsebak.raspberrypi.ui.RaspberryPIRunConfigurationEditor;
import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

//ApplicationConfiguration RunConfigurationBase

public class RaspberryPIRunConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule> implements CommonJavaRunConfigurationParameters, SingleClassConfiguration, RefactoringListenerProvider {
    public static final String DEFAULT_HOSTNAME = "0.0.0.0";
    public static final String DEFAULT_DEBUG_POST = "4000";
    public static final boolean DEFAULT_RUN_AS_ROOT = true;
    public static final String DEFAULT_DISPLAY = ":0";
    private final Map<String, String> myEnvs = new LinkedHashMap<String, String>();
    public String MAIN_CLASS_NAME;
    public String VM_PARAMETERS;
    public String PROGRAM_PARAMETERS;
    public String WORKING_DIRECTORY;
    public boolean ALTERNATIVE_JRE_PATH_ENABLED;
    public String ALTERNATIVE_JRE_PATH;
    public boolean ENABLE_SWING_INSPECTOR;
    public String ENV_VARIABLES;
    public boolean PASS_PARENT_ENVS;

    private RaspberryPIRunnerParameters raspberryPIRunnerParameters = new RaspberryPIRunnerParameters();
    private Project project;
    protected RaspberryPIRunConfiguration(final Project project, final ConfigurationFactory factory) {
        super(new JavaRunConfigurationModule(project, false), factory);
        this.project = project;
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        SettingsEditorGroup<RaspberryPIRunConfiguration> group = new SettingsEditorGroup<RaspberryPIRunConfiguration>();
        group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new RaspberryPIRunConfigurationEditor(getProject()));
        JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group);
        group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<RaspberryPIRunConfiguration>());
        return group;
    }

    protected RaspberryPIRunnerParameters createRunnerParametersInstance() {
        return new RaspberryPIRunnerParameters();
    }

    @Override
    public Collection<Module> getValidModules() {
        return JavaRunConfigurationModule.getModulesForClass(this.getProject(), this.MAIN_CLASS_NAME);
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
        super.readExternal(element);
        raspberryPIRunnerParameters = createRunnerParametersInstance();
        XmlSerializer.deserializeInto(raspberryPIRunnerParameters, element);
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
        super.writeExternal(element);
        if (raspberryPIRunnerParameters != null) {
            XmlSerializer.serializeInto(raspberryPIRunnerParameters, element);
        }
    }

    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
        final JavaCommandLineState state = new RemoteJavaApplicationCommandLineState(this, env);
        JavaRunConfigurationModule module = getConfigurationModule();
        state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject(), module.getSearchScope()));
        return state;
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        checkJavaSettings();
        checkPiSettings();
    }

    private void checkPiSettings() throws RuntimeConfigurationException {
        RaspberryPIRunnerParameters rp = this.getRunnerParameters();
        if (rp.getDisplay() == null || rp.getDisplay().isEmpty()) {
            throw new RuntimeConfigurationException(PIBundle.getString("pi.invalid.xdisplay"));
        }
        if (rp.getHostname() == null || rp.getHostname().isEmpty()) {
            throw new RuntimeConfigurationException(PIBundle.getString("pi.invalid.hostname"));
        }
        if (rp.getPort() == null || rp.getPort().isEmpty()) {
            throw new RuntimeConfigurationException(PIBundle.getString("pi.invalid.port"));
        }
        if (rp.getUsername() == null || rp.getUsername().isEmpty()) {
            throw new RuntimeConfigurationException(PIBundle.getString("pi.invalid.username"));
        }
    }

    private void checkJavaSettings() throws RuntimeConfigurationException {
        JavaParametersUtil.checkAlternativeJRE(this);
        JavaRunConfigurationModule var1 = this.getConfigurationModule();
        PsiClass var2 = var1.checkModuleAndClassName(this.MAIN_CLASS_NAME, ExecutionBundle.message("no.main.class.specified.error.text"));
        if (!PsiMethodUtil.hasMainMethod(var2)) {
            throw new RuntimeConfigurationWarning(ExecutionBundle.message("main.method.not.found.in.class.error.message", this.MAIN_CLASS_NAME));
        } else {
            ProgramParametersUtil.checkWorkingDirectoryExist(this, this.getProject(), var1.getModule());
            JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
        }
    }

    public RaspberryPIRunnerParameters getRunnerParameters() {
        return raspberryPIRunnerParameters;
    }

    @Nullable
    public PsiClass getMainClass() {
        return this.getConfigurationModule().findClass(this.MAIN_CLASS_NAME);
    }

    public void setMainClass(PsiClass var1) {
        Module var2 = this.getConfigurationModule().getModule();
        this.setMainClassName(JavaExecutionUtil.getRuntimeQualifiedName(var1));
        this.setModule(JavaExecutionUtil.findModule(var1));
        this.restoreOriginalModule(var2);
    }

    @Nullable
    public String suggestedName() {
        return this.MAIN_CLASS_NAME == null?null:JavaExecutionUtil.getPresentableClassName(this.MAIN_CLASS_NAME);
    }

    public String getActionName() {
        return this.MAIN_CLASS_NAME != null && this.MAIN_CLASS_NAME.length() != 0?ProgramRunnerUtil.shortenName(JavaExecutionUtil.getShortClassName(this.MAIN_CLASS_NAME), 6) + ".main()":null;
    }

    public void setMainClassName(String var1) {
        this.MAIN_CLASS_NAME = var1;
    }

    @Override
    public String getVMParameters() {
        return this.VM_PARAMETERS;
    }

    @Override
    public void setVMParameters(String var1) {
        this.VM_PARAMETERS = var1;
    }

    @Override
    public boolean isAlternativeJrePathEnabled() {
        return this.ALTERNATIVE_JRE_PATH_ENABLED;
    }

    @Override
    public void setAlternativeJrePathEnabled(boolean var1) {
        this.ALTERNATIVE_JRE_PATH_ENABLED = var1;

    }

    @Override
    public String getAlternativeJrePath() {
        return this.ALTERNATIVE_JRE_PATH;
    }

    @Override
    public void setAlternativeJrePath(String s) {
        this.ALTERNATIVE_JRE_PATH = s;
    }

    @Nullable
    @Override
    public String getRunClass() {
        return this.MAIN_CLASS_NAME;
    }

    @Nullable
    @Override
    public String getPackage() {
        return null;
    }

    @Nullable
    @Override
    public String getProgramParameters() {
        return this.PROGRAM_PARAMETERS;
    }

    @Override
    public void setProgramParameters(@Nullable String var1) {
        this.PROGRAM_PARAMETERS = var1;
    }

    @Nullable
    @Override
    public String getWorkingDirectory() {
        return ExternalizablePath.localPathValue(this.WORKING_DIRECTORY);
    }

    @Override
    public void setWorkingDirectory(@Nullable String s) {
        this.WORKING_DIRECTORY = ExternalizablePath.urlValue(s);
    }

    @NotNull
    @Override
    public Map<String, String> getEnvs() {
        return myEnvs;
    }

    @Override
    public void setEnvs(@NotNull final Map<String, String> envs) {
        myEnvs.clear();
        myEnvs.putAll(envs);
    }

    @Override
    public boolean isPassParentEnvs() {
        return this.PASS_PARENT_ENVS;
    }

    @Override
    public void setPassParentEnvs(boolean b) {
        this.PASS_PARENT_ENVS = b;
    }

    @Nullable
    @Override
    public RefactoringElementListener getRefactoringElementListener(PsiElement psiElement) {
        RefactoringElementListener var2 = RefactoringListeners.getClassOrPackageListener(psiElement, new RefactoringListeners.SingleClassConfigurationAccessor(this));
        return RunConfigurationExtension.wrapRefactoringElementListener(psiElement, this, var2);
    }

    /** Class to configure command line state of the dev app server **/
    public static class RemoteJavaApplicationCommandLineState extends JavaCommandLineState {

        private final RaspberryPIRunConfiguration configuration;

        public RemoteJavaApplicationCommandLineState(@NotNull final RaspberryPIRunConfiguration configuration,
                                               final ExecutionEnvironment environment) {
            super(environment);
            this.configuration = configuration;
        }

        @NotNull
        @Override
        protected OSProcessHandler startProcess() throws ExecutionException {
            OSProcessHandler handler = super.startProcess();
            handler.setShouldDestroyProcessRecursively(true);
            final RunnerSettings runnerSettings = getRunnerSettings();
            JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(configuration, handler, runnerSettings);
            return handler;
        }

        @Override
        protected JavaParameters createJavaParameters() throws ExecutionException {
            final JavaParameters params = new JavaParameters();
            final JavaRunConfigurationModule module = configuration.getConfigurationModule();

            final int classPathType = JavaParametersUtil.getClasspathType(module,
                    configuration.MAIN_CLASS_NAME,
                    false);
            final String jreHome = configuration.ALTERNATIVE_JRE_PATH_ENABLED ? configuration.ALTERNATIVE_JRE_PATH : null;
            JavaParametersUtil.configureModule(module, params, classPathType, jreHome);
            params.setMainClass(configuration.MAIN_CLASS_NAME);
            return params;
        }

        @Override
        protected GeneralCommandLine createCommandLine() throws ExecutionException {
            return super.createCommandLine();
        }
    }
}