# ALTER LOAD

## Description

Changes the priority of a Broker Load job that is in the **QUEUEING** or **LOADING** state.

> **NOTE**
>
> Changing the priority of a Broker Load job that is in the **LOADING** state does not affect the execution of the job.

## Syntax

```SQL
ALTER LOAD FOR <label_name>
properties
(
    'priority'='{LOWEST | LOW | NORMAL | HIGH | HIGHEST}'
)
```

## Parameters

| **Parameter** | **Required** | Description                                                  |
| ------------- | ------------ | ------------------------------------------------------------ |
| label_name    | Yes          | The label of the load job. Format: `[<database_name>.]<label_name>`. See [BROKER LOAD](../data-manipulation/BROKER%20LOAD.md#label). |
| priority      | Yes          | The new priority that you want to specify for the load job. Valid values: `LOWEST`, `LOW`, `NORMAL`, `HIGH`, and `HIGHEST`. See [BROKER LOAD](../data-manipulation/BROKER%20LOAD.md). |

## Examples

Suppose that you have a Broker Load job whose label is `test_db.label1` and the job is in the **QUEUEING** state. If you want to run the job at the soonest, you can run the following command to change the priority of the job to `HIGHEST`:

```SQL
ALTER LOAD FOR test_db.label1
properties
(
    'priority'='HIGHEST'
);
```
