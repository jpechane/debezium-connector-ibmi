package io.debezium.connector.db2as400;

import java.util.Properties;

import org.apache.kafka.connect.errors.ConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.config.CommonConnectorConfig;
import io.debezium.config.Configuration;
import io.debezium.config.Field;
import io.debezium.schema.AbstractTopicNamingStrategy;
import io.debezium.schema.SchemaTopicNamingStrategy;
import io.debezium.spi.schema.DataCollectionId;
import io.debezium.util.BoundedConcurrentHashMap;
import io.debezium.util.Collect;

public class NoPrefixNamingScheme extends AbstractTopicNamingStrategy<DataCollectionId> {

    private final boolean multiPartitionMode;

    public NoPrefixNamingScheme(Properties props) {
        super(props);
        this.multiPartitionMode = props.get(CommonConnectorConfig.MULTI_PARTITION_MODE) == null ? false
                : Boolean.parseBoolean(props.get(CommonConnectorConfig.MULTI_PARTITION_MODE).toString());
    }

    public NoPrefixNamingScheme(Properties props, boolean multiPartitionMode) {
        super(props);
        this.multiPartitionMode = multiPartitionMode;
    }

    public static SchemaTopicNamingStrategy create(CommonConnectorConfig config) {
        return create(config, false);
    }

    public static SchemaTopicNamingStrategy create(CommonConnectorConfig config, boolean multiPartitionMode) {
        return new SchemaTopicNamingStrategy(config.getConfig().asProperties(), multiPartitionMode);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(NoPrefixNamingScheme.class);

    
    @Override
    public void configure(Properties props) {
        Configuration config = Configuration.from(props);
        final Field.Set configFields = Field.setOf(
        		// excluding CommonConnectorConfig.TOPIC_PREFIX
                TOPIC_DELIMITER,
                TOPIC_CACHE_SIZE,
                TOPIC_TRANSACTION,
                TOPIC_HEARTBEAT_PREFIX);

        if (!config.validateAndRecord(configFields, LOGGER::error)) {
            throw new ConnectException("Unable to validate config.");
        }

        topicNames = new BoundedConcurrentHashMap<>(
                config.getInteger(TOPIC_CACHE_SIZE),
                10,
                BoundedConcurrentHashMap.Eviction.LRU);
        delimiter = config.getString(TOPIC_DELIMITER);
        heartbeatPrefix = config.getString(TOPIC_HEARTBEAT_PREFIX);
        transaction = config.getString(TOPIC_TRANSACTION);
        prefix = config.getString(CommonConnectorConfig.TOPIC_PREFIX);
        assert prefix != null;
    }
    
    @Override
    public String dataChangeTopic(DataCollectionId id) {
        String topicName;
        if (multiPartitionMode) {
            topicName = mkString(Collect.arrayListOf("", id.parts()), delimiter);
        }
        else {
            topicName = mkString(Collect.arrayListOf("", id.schemaParts()), delimiter);
        }
        return topicNames.computeIfAbsent(id, t -> sanitizedTopicName(topicName));
    }
}