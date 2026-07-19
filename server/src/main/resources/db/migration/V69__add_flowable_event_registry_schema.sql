DROP PROCEDURE IF EXISTS wfh_init_flowable_event_registry_schema_if_needed;

DELIMITER $$
CREATE PROCEDURE wfh_init_flowable_event_registry_schema_if_needed()
BEGIN
DECLARE flowable_event_table_count INT DEFAULT 0;

SELECT COUNT(*) INTO flowable_event_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'FLW_EVENT_DEPLOYMENT',
      'FLW_EVENT_RESOURCE',
      'FLW_EVENT_DEFINITION',
      'FLW_CHANNEL_DEFINITION'
  );

IF flowable_event_table_count = 0 THEN
create table FLW_EVENT_DEPLOYMENT (
    ID_ varchar(255) not null,
    NAME_ varchar(255),
    CATEGORY_ varchar(255),
    DEPLOY_TIME_ datetime(3),
    TENANT_ID_ varchar(255),
    PARENT_DEPLOYMENT_ID_ varchar(255),
    primary key (ID_)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_bin;

create table FLW_EVENT_RESOURCE (
    ID_ varchar(255) not null,
    NAME_ varchar(255),
    DEPLOYMENT_ID_ varchar(255),
    RESOURCE_BYTES_ longblob,
    primary key (ID_)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_bin;

create table FLW_EVENT_DEFINITION (
    ID_ varchar(255) not null,
    NAME_ varchar(255),
    VERSION_ integer,
    KEY_ varchar(255),
    CATEGORY_ varchar(255),
    DEPLOYMENT_ID_ varchar(255),
    TENANT_ID_ varchar(255),
    RESOURCE_NAME_ varchar(255),
    DESCRIPTION_ varchar(255),
    primary key (ID_)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_bin;

create unique index ACT_IDX_EVENT_DEF_UNIQ
    on FLW_EVENT_DEFINITION(KEY_, VERSION_, TENANT_ID_);

create table FLW_CHANNEL_DEFINITION (
    ID_ varchar(255) not null,
    NAME_ varchar(255),
    VERSION_ integer,
    KEY_ varchar(255),
    CATEGORY_ varchar(255),
    DEPLOYMENT_ID_ varchar(255),
    CREATE_TIME_ datetime(3),
    TENANT_ID_ varchar(255),
    RESOURCE_NAME_ varchar(255),
    DESCRIPTION_ varchar(255),
    TYPE_ varchar(255),
    IMPLEMENTATION_ varchar(255),
    primary key (ID_)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_bin;

create unique index ACT_IDX_CHANNEL_DEF_UNIQ
    on FLW_CHANNEL_DEFINITION(KEY_, VERSION_, TENANT_ID_);
END IF;
END$$
DELIMITER ;

CALL wfh_init_flowable_event_registry_schema_if_needed();
DROP PROCEDURE IF EXISTS wfh_init_flowable_event_registry_schema_if_needed;
