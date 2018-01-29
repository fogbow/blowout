package org.fogbowcloud.blowout.core.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.infrastructure.model.AbstractResource;

public class TaskProcessImpl implements TaskProcess {

	private static final Logger LOGGER = Logger.getLogger(TaskProcessImpl.class);

	private static final String ENV_HOST = "HOST";
	private static final String ENV_SSH_PORT = "SSH_PORT";
	private static final String ENV_SSH_USER = "SSH_USER";
	private static final String ENV_PRIVATE_KEY_FILE = "PRIVATE_KEY_FILE";

	private static final String METADATA_SSH_HOST = "metadataSSHHost";
	private static final String METADATA_SSH_PORT = "metadataSSHPort";

	private static final String UserID = "UUID";

	private final String taskId;
	private TaskState status;
	private final Specification spec;
	private final List<Command> commandList;
	private AbstractResource resource;
	private String localCommandInterpreter;
	private String processId;
	private String userId;

	public TaskProcessImpl(String taskId, List<Command> commandList, Specification spec,
			String userId) {
		this.processId = UUID.randomUUID().toString();
		this.taskId = taskId;
		this.status = TaskState.READY;
		this.spec = spec;
		this.commandList = commandList;
		this.userId = userId;
	}

	@Override
	public TaskExecutionResult executeTask(AbstractResource resource) {
		this.resource = resource;
		this.localCommandInterpreter = resource.getLocalCommandInterpreter();
		this.setStatus(TaskState.RUNNING);

		TaskExecutionResult taskExecutionResult = new TaskExecutionResult();

		for (Command command : this.getCommands()) {
			LOGGER.trace("Command " + command.getCommand());
			LOGGER.trace("Command Type " + command.getType());

			String commandString = this.getExecutableCommandString(command);

			taskExecutionResult = this.executeCommandString(commandString, command.getType(),
					resource);

			LOGGER.trace("Command result: " + taskExecutionResult.getExitValue());
			if (taskExecutionResult.getExitValue() != TaskExecutionResult.OK) {
				if (taskExecutionResult.getExitValue() == TaskExecutionResult.TIMEOUT) {
					this.setStatus(TaskState.TIMEDOUT);
				} else if (taskExecutionResult
						.getExitValue() == TaskExecutionResult.SU_COMMAND_BAD_ARGUMENTS) {
					LOGGER.error("Bad arguments to script su_command.c");
					this.setStatus(TaskState.FAILED);
				} else {
					this.setStatus(TaskState.FAILED);
				}
				break;
			}
		}
		if (!this.getStatus().equals(TaskState.FAILED)
				&& !this.getStatus().equals(TaskState.TIMEDOUT)) {
			this.setStatus(TaskState.FINISHED);
		}

		return taskExecutionResult;
	}

	private String getExecutableCommandString(Command command) {
		return command.getCommand();
	}

	protected TaskExecutionResult executeCommandString(String commandString, Command.Type type,
			AbstractResource resource) {

		TaskExecutionResult taskExecutionResult = new TaskExecutionResult();
		int returnValue;

		Map<String, String> additionalVariables = getAdditionalEnvVariables(resource);
		
		try {
			if (type.equals(Command.Type.LOCAL)) {
				Process localProc = startLocalProcess(commandString, additionalVariables);
				returnValue = localProc.waitFor();
			} else {
				Process remoteProc = startRemoteProcess(commandString, additionalVariables);
				returnValue = remoteProc.waitFor();
			}
		} catch (Exception e) {
			returnValue = TaskExecutionResult.NOK;
		}

		taskExecutionResult.finish(returnValue);
		return taskExecutionResult;
	}

	private Process startRemoteProcess(String commandString,
			Map<String, String> additionalVariables) throws IOException {

		String commandInterpreter = "/bin/bash";
		String commandInterpreterTags = "-c";
		String command = "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i "
				+ additionalVariables.get(ENV_PRIVATE_KEY_FILE) + " "
				+ additionalVariables.get(ENV_SSH_USER) + "@" + additionalVariables.get(ENV_HOST)
				+ " -p " + additionalVariables.get(ENV_SSH_PORT) + " "
				+ this.parseEnvironVariable(commandString, additionalVariables);

		ProcessBuilder builder = new ProcessBuilder(commandInterpreter, commandInterpreterTags,
				command);

		LOGGER.debug(command);

		return builder.start();
	}

	private Process startLocalProcess(String command, Map<String, String> additionalEnvVariables)
			throws IOException {
		String GID = "9999"; 
		ProcessBuilder builder = new ProcessBuilder(localCommandInterpreter, TaskProcessImpl.UserID,
				GID, command);
		
		if (additionalEnvVariables == null || additionalEnvVariables.isEmpty()) {
			return builder.start();
		}

		for (String envVariable : additionalEnvVariables.keySet()) {
			builder.environment().put(envVariable, additionalEnvVariables.get(envVariable));
		}
		LOGGER.debug("Command: " + builder.command().toString());
		return builder.start();
	}

	private String parseEnvironVariable(String commandString,
			Map<String, String> additionalVariables) {
		for (String envVar : additionalVariables.keySet()) {
			commandString.replace(envVar, additionalVariables.get(envVar));
		}
		return commandString;
	}

	protected Map<String, String> getAdditionalEnvVariables(AbstractResource resource) {
		Map<String, String> additionalEnvVar = new HashMap<>();

		additionalEnvVar.put(ENV_HOST, resource.getMetadataValue(METADATA_SSH_HOST));
		additionalEnvVar.put(ENV_SSH_PORT, resource.getMetadataValue(METADATA_SSH_PORT));
		LOGGER.debug(ENV_HOST + ": " + resource.getMetadataValue(METADATA_SSH_HOST));
		LOGGER.debug(ENV_SSH_PORT + ": " + resource.getMetadataValue(METADATA_SSH_PORT));

		String envSSHUser;
		if (this.spec.getUsername() != null && !this.spec.getUsername().trim().isEmpty()) {
			envSSHUser = this.spec.getUsername();
		} else {
			envSSHUser = resource.getMetadataValue(ENV_SSH_USER);
		}
		additionalEnvVar.put(ENV_SSH_USER, envSSHUser);
		LOGGER.debug(ENV_SSH_USER + ": " + envSSHUser);

		additionalEnvVar.put(ENV_PRIVATE_KEY_FILE, this.spec.getPrivateKeyFilePath());
		LOGGER.debug(ENV_PRIVATE_KEY_FILE + ": " + this.spec.getPrivateKeyFilePath());

		additionalEnvVar.put(UserID, this.userId);

		// TODO: getEnvVariables from task
		return additionalEnvVar;
	}

	public String getProcessId() {
		return this.processId;
	}

	@Override
	public String getTaskId() {
		return this.taskId;
	}

	@Override
	public List<Command> getCommands() {
		return new ArrayList<>(this.commandList);
	}

	@Override
	public TaskState getStatus() {
		return this.status;
	}

	@Override
	public void setStatus(TaskState status) {
		this.status = status;
	}

	@Override
	public AbstractResource getResource() {
		return this.resource;
	}

	public void setResource(AbstractResource resource) {
		this.resource = resource;
	}

	@Override
	public Specification getSpecification() {
		return this.spec;
	}
}
