package org.fogbowcloud.blowout.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.blowout.core.model.Specification;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.blowout.infrastructure.model.FogbowResource;
import org.json.JSONException;
import org.json.JSONObject;

public class FogbowResourceDatastore {

	private static final Logger LOGGER = Logger.getLogger(FogbowResourceDatastore.class);

	protected static final String FOGBOW_RESOURCE_TABLE_NAME = "fogbow_resource_store";

	protected static final String RESOURCE_ID = "resource_id";
	protected static final String ORDER_ID = "order_id";
	protected static final String SPEC = "spec";

	private static final String INSERT_FOGBOW_RESOURCE_SQL = "INSERT INTO "
			+ FOGBOW_RESOURCE_TABLE_NAME + " VALUES(?,?,?)";
	private static final String SELECT_REQUEST_ID = "SELECT * FROM " + FOGBOW_RESOURCE_TABLE_NAME;
	private static final String DELETE_ALL_CONTENT_SQL = "DELETE FROM "
			+ FOGBOW_RESOURCE_TABLE_NAME;
	private static final String DELETE_BY_RESOURCE_ID_SQL = DELETE_ALL_CONTENT_SQL + " WHERE "
			+ RESOURCE_ID + "=? ";

	private String dataStoreURL;
	private Properties properties;

	public FogbowResourceDatastore(Properties properties) {
		this.properties = properties;
		this.dataStoreURL = this.properties.getProperty(AppPropertiesConstants.DB_DATASTORE_URL);

		LOGGER.debug("DatastoreURL: " + this.dataStoreURL);

		String SQLCommand = "CREATE TABLE IF NOT EXISTS " + FOGBOW_RESOURCE_TABLE_NAME + "("
				+ RESOURCE_ID + " VARCHAR(255) PRIMARY KEY," + ORDER_ID + " VARCHAR(255),"
				+ SPEC + " TEXT " + ")";

		Statement statement = null;
		Connection connection = null;
		try {
			LOGGER.debug("DatastoreURL: " + this.dataStoreURL);

			connection = this.getConnection();
			connection.setAutoCommit(false);
			statement = connection.createStatement();
			statement.execute(SQLCommand);
			statement.close();
			connection.commit();

		} catch (Exception e) {
			LOGGER.error("Error while initializing the DataStore.", e);
		} finally {
			this.close(statement, connection);
		}
	}

	public boolean addFogbowResource(FogbowResource fogbowResource) {
		LOGGER.debug("Adding resource id: " + fogbowResource.getId());

		PreparedStatement statements = null;
		Connection connection = null;
		try {
			connection = this.getConnection();
			connection.setAutoCommit(false);
			statements = this.prepare(connection, INSERT_FOGBOW_RESOURCE_SQL);

			String resourceSpecification = this.getResourceSpecification(fogbowResource);

			this.insertResourceStatements(statements, fogbowResource, resourceSpecification);

			boolean result = statements.execute();
			connection.commit();
			return result;

		} catch (SQLException e) {
			LOGGER.error("Couldn't store the current resource id", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
			return false;
		} finally {
			this.close(statements, connection);
		}

	}

	public boolean addResourceIds(List<FogbowResource> fogbowResources) {
		LOGGER.debug("Adding resource ids");
		PreparedStatement statements = null;
		Connection connection = null;
		try {
			connection = this.getConnection();
			connection.setAutoCommit(false);
			statements = this.prepare(connection, INSERT_FOGBOW_RESOURCE_SQL);

			for (FogbowResource fogbowResource : fogbowResources) {
				String resourceSpecification = this.getResourceSpecification(fogbowResource);

				this.insertResourceStatements(statements, fogbowResource, resourceSpecification);

				statements.addBatch();
				connection.rollback();
			}

			if (this.hasBatchExecutionError(statements.executeBatch())) {
				return false;
			}
			connection.commit();
			return true;

		} catch (SQLException e) {
			LOGGER.error("Couldn't store the current resource id", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
			return false;
		} finally {
			this.close(statements, connection);
		}
	}

	public List<FogbowResource> getAllFogbowResources() {
		List<FogbowResource> fogbowResources = new ArrayList<>();
		Statement statement = null;
		Connection connection = null;
		try {
			connection = this.getConnection();
			statement = connection.createStatement();
			statement.execute(SELECT_REQUEST_ID);
			ResultSet result = statement.getResultSet();

			while (result.next()) {
				FogbowResource fogbowresource = this.createFogbowResource(result);
				fogbowResources.add(fogbowresource);
			}

			return fogbowResources;
		} catch (Exception e) {
			LOGGER.error("Couldn't recover request Ids from DB", e);
			return null;
		} finally {
			this.close(statement, connection);
		}
	}

	public boolean deleteFogbowResourceById(FogbowResource fogbowResource) {
		return deleteFogbowResourceById(fogbowResource.getId());
	}


	public boolean deleteFogbowResourceById(String fgbowResourceId) {
		LOGGER.debug("Deleting resource id: " + fgbowResourceId);

		PreparedStatement statement = null;
		Connection connection = null;
		try {
			connection = this.getConnection();
			connection.setAutoCommit(false);

			statement = this.prepare(connection, DELETE_BY_RESOURCE_ID_SQL);
			statement.setString(1, fgbowResourceId);

			boolean result = statement.execute();
			connection.commit();
			return result;

		} catch (SQLException e) {
			LOGGER.error("Couldn't delete the resource " + fgbowResourceId, e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
			return false;

		} finally {
			this.close(statement, connection);
		}
	}

	public boolean deleteAll() {
		LOGGER.debug("Deleting all resources");

		PreparedStatement statement = null;
		Connection connection = null;
		try {
			connection = this.getConnection();
			connection.setAutoCommit(false);

			statement = connection.prepareStatement(DELETE_ALL_CONTENT_SQL);
			statement.executeUpdate();

			connection.commit();
			return true;
		} catch (SQLException e) {
			LOGGER.error("Couldn't delete all resource ids", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
			return false;
		} finally {
			this.close(statement, connection);
		}
	}

	private FogbowResource createFogbowResource(ResultSet result)
			throws SQLException, JSONException {

		String id = result.getString(RESOURCE_ID);
		String orderId = result.getString(ORDER_ID);
		String specification = result.getString(SPEC);

		JSONObject jsonSpec = new JSONObject(specification);
		Specification spec = Specification.fromJSON(jsonSpec);

		return new FogbowResource(id, orderId, spec);
	}

	private String getResourceSpecification(FogbowResource fogbowResource) {
		String spec = null;
		if (fogbowResource.getRequestedSpec() != null) {
			JSONObject json = fogbowResource.getRequestedSpec().toJSON();
			spec = json.toString();
		}
		return spec;
	}

	private void insertResourceStatements(PreparedStatement statements,
			FogbowResource fogbowResource, String resourceSpecification) throws SQLException {

		statements.setString(1, fogbowResource.getId());
		statements.setString(2, fogbowResource.getOrderId());

		if (resourceSpecification == null) {
			statements.setNull(3, Types.LONGVARCHAR);
		} else {
			statements.setString(3, resourceSpecification);
		}
	}

	private Connection getConnection() throws SQLException {
		try {
			return DriverManager.getConnection(this.dataStoreURL);
		} catch (SQLException e) {
			LOGGER.error("Error while getting a new connection from the connection pool.", e);
			throw e;
		}
	}

	private void close(Statement statement, Connection connection) {
		if (statement != null) {
			try {
				if (!statement.isClosed()) {
					statement.close();
				}
			} catch (SQLException e) {
				LOGGER.error("Couldn't close statement of Fogbow Resource Datastore");
			}
		}

		if (connection != null) {
			try {
				if (!connection.isClosed()) {
					connection.close();
				}
			} catch (SQLException e) {
				LOGGER.error("Couldn't close connection of Fogbow Resource Datastore");
			}
		}
	}

	private PreparedStatement prepare(Connection connection, String statement) throws SQLException {
		return connection.prepareStatement(statement);
	}

	private boolean hasBatchExecutionError(int[] executeBatch) {
		for (int execution : executeBatch) {
			if (execution == PreparedStatement.EXECUTE_FAILED) {
				return true;
			}
		}
		return false;
	}
}
