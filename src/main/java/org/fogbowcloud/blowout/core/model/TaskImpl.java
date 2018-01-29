package org.fogbowcloud.blowout.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.model.Command.Type;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TaskImpl implements Task {

	private static final long serialVersionUID = -8068456932331499162L;

	private static final Logger LOGGER = Logger.getLogger(TaskImpl.class);

    public static final String METADATA_TASK_TIMEOUT = "task_timeout";
    public static final String METADATA_MAX_RESOURCE_CONN_RETRIES = "max_conn_retries";
    // The constants below are used on Arrebol
    // TODO? move them into arrebol
	public static final String METADATA_REMOTE_OUTPUT_FOLDER = "remote_output_folder";
	public static final String METADATA_LOCAL_OUTPUT_FOLDER = "local_output_folder";
	public static final String METADATA_SANDBOX = "sandbox";
	public static final String METADATA_REMOTE_COMMAND_EXIT_PATH = "remote_command_exit_path";

	private String id;
	private Specification spec;
    private TaskState state;
	private String uuid;
    private boolean isFailed;
    private int retries;
	private boolean isFinished;
	private long startedRunningAt;

	private List<Command> commands = new ArrayList<>();
	private Map<String, String> metadata = new HashMap<>();
    private List<String> processes = new ArrayList<>();


	public TaskImpl(String id, Specification spec, String uuid) {
		this.id = id;
		this.spec = spec;
		this.state = TaskState.READY;
		this.uuid = uuid;
		this.retries = -1;
		this.isFinished = false;
		this.isFailed = false;
		this.startedRunningAt = Long.MAX_VALUE;
	}
	
	@Override
	public boolean checkTimeOuted() {
		String timeOutRaw = this.getMetadata(METADATA_TASK_TIMEOUT);
		if (timeOutRaw == null || timeOutRaw.trim().isEmpty()) {
			return false;
		}
		long timeOut;
		try {
			timeOut = Long.parseLong(timeOutRaw);
		} catch (NumberFormatException e) {
			LOGGER.error("Timeout badly formated, ignoring it: ", e);
			return false;
		}
        return (System.currentTimeMillis() - this.startedRunningAt) > timeOut;
	}

	@Override
	public boolean mayRetry() {
        return this.getMetadata(METADATA_MAX_RESOURCE_CONN_RETRIES) != null && this.getRetries() <= Integer.parseInt(this.getMetadata(METADATA_MAX_RESOURCE_CONN_RETRIES));
    }

	@Override
	public List<Command> getCommandsByType(Type commandType) {
		List<Command> commandsToReturn = new ArrayList<>();
		for (Command command : getAllCommands()) {
			if (command.getType().equals(commandType)) {
				commandsToReturn.add(command);
			}
		}
		return commandsToReturn;
	}

	@Override
	public Task clone() {
		TaskImpl taskClone = new TaskImpl(UUID.randomUUID().toString() + "_clonedFrom_" + getId(),
				getSpecification(), getUUID());
		Map<String, String> allMetadata = getAllMetadata();
		for (String attribute : allMetadata.keySet()) {
			taskClone.putMetadata(attribute, allMetadata.get(attribute));
		}

		for (Command command : this.getAllCommands()) {
			taskClone.addCommand(command);
		}
		return taskClone;
	}

	@Override
	public void startedRunning() {
		this.startedRunningAt = System.currentTimeMillis();
		this.retries++;
	}

	@Override
	public void finish(){
		this.isFinished = true;
	}

	@Override
	public boolean isFinished() {
		return this.isFinished;
	}

	@Override
	public void fail() {
		this.isFailed = true;
	}

	@Override
	public boolean isFailed() {
		return this.isFailed;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public void addProcessId(String procId) {
		this.processes.add(procId);
	}

	@Override
	public List<String> getProcessId() {
		return this.processes;
	}

	@Override
	public TaskState getState() {
		return this.state;
	}

	@Override
	public void setState(TaskState state) {
		this.state = state;
	}

	@Override
	public String getUUID() {
		return this.uuid;
	}

	@Override
	public Specification getSpecification() {
		return this.spec;
	}

	@Override
	public int getRetries() { 
		return this.retries;
	}

	@Override
	public void setRetries(int retries) {
		this.retries = retries;		
	}

	@Override
	public void addCommand(Command command) {
		this.commands.add(command);
	}

	@Override
	public List<Command> getAllCommands() {
		return this.commands;
	}
	
	@Override
	public int getNumberOfCommands() {
		return this.commands.size();
	}

	@Override
	public void putMetadata(String attributeName, String value) {
		this.metadata.put(attributeName, value);
	}

	@Override
	public String getMetadata(String attributeName) {
		return this.metadata.get(attributeName);
	}

	@Override
	public Map<String, String> getAllMetadata() {
		return this.metadata;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((spec == null) ? 0 : spec.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TaskImpl other = (TaskImpl) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (spec == null) {
            return other.spec == null;
		} else return spec.equals(other.spec);
    }
	
	public JSONObject toJSON() {
		try {
			JSONObject task = new JSONObject();
			task.put("isFinished", this.isFinished());
			task.put("isFailed", this.isFailed());
			task.put("id", this.getId());
			task.put("spec", this.getSpecification().toJSON());
			task.put("retries", this.getRetries());
			task.put("uuid", this.getUUID());
			JSONArray commands = new JSONArray();
			for (Command command : this.getAllCommands()) {
				commands.put(command.toJSON());
			}
			task.put("commands", commands);
			JSONObject metadata = new JSONObject();
			for (Entry<String, String> entry : this.getAllMetadata().entrySet()) {
				metadata.put(entry.getKey(), entry.getValue());
			}
			task.put("metadata", metadata);
			return task;
		} catch (JSONException e) {
			LOGGER.debug("Error while trying to create a JSON from task", e);
			return null;
		}
	}

	public static Task fromJSON(JSONObject taskJSON) {
		Specification specification = Specification.fromJSON(taskJSON.optJSONObject("spec"));
		Task task = new TaskImpl(taskJSON.optString("id"), specification, taskJSON.optString("uuid"));
		task.setRetries(taskJSON.optInt("retries"));
		if (taskJSON.optBoolean("isFinished")) {
			task.finish();
		}
		if (taskJSON.optBoolean("isFailed")) {
			task.fail();
		}
		
		JSONArray commands = taskJSON.optJSONArray("commands");
		for (int i = 0; i < commands.length(); i++) {
			task.addCommand(Command.fromJSON(commands.optJSONObject(i)));
		}
		
		JSONObject metadata = taskJSON.optJSONObject("metadata");
		Iterator<?> metadataKeys = metadata.keys();
		while (metadataKeys.hasNext()) {
			String key = (String) metadataKeys.next();
			task.putMetadata(key, metadata.optString(key));
		}
		return task;
	}

}
