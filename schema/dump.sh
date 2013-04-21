#!/bin/sh

mysqldump --no-data --add-drop-table --add-drop-database --databases -u root -p apm > schema.sql
