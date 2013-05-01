# APM

A statistics aggregation/graph server, using a REST interface with a (pretty) built-in frontend.

# Usage

APM is used to store series of numeric values, and to plot those values over time.

## Topology

Values are referenced by their name, which are strings containing no `/` characters. Value references can be organized 
in a tree-structure as if they were directories.

For example, if the following references exist:
```
/tld/dir1/ref1
/tld/dir1/ref2
/tld/ref3
/ref4
```
    
The following calls will return:
```
> GET /tld/dir1
[ { "type":"ref", "name":"ref1" },
  { "type":"ref", "name":"ref2" } ]
```

```
> GET /tld
[ { "type":"dir", "name":"dir1" },
  { "type":"ref", "name":"ref3" } ]
```

```
> GET /
[ { "type":"dir", "name":"tld" },
  { "type":"ref", "name":"ref4" } ]
```

## Working with values

There is no procedure for initializing references or directories, you simply use them as if they were always there.
There are multiple ways to post a new value for a reference. All new values are implicitely stored with the timestamp
they were received, which is later used for graphing.

To post the absolute value `50`:
```
> POST /dir/ref1/:abs/50
ok
```

To simply increment/decrement from the last posted value:
```
> POST /dir/ref1/:inc
ok
```

```
> POST /dir/ref1/:dec
ok
```

Note that if a reference which didn't previously exist is incremented it is assumed to have been 0 BEFORE the increment
(in other words, after the POST it will be 1).

You can increment/decrement by amounts other than 1 as well:
```
> POST /dir/ref1/:inc/5
ok
```

```
> POST /dir/ref1/:dec/5
ok
```

In the case of negative numbers, `:inc/-5 == :dec/5` and `:inc/5 == :dec/-5`.

## Retrieving values

In the topology section it is shown that performing a GET on a directory returns the directory's contents. Performing a
GET on a reference use `:all` returns that reference's values and their timestamps (as UNIX timestamps).

```
> GET /dir/ref1/:all
[ { "ts":..., "val":1 },
  { "ts":..., "val":2 },
  ... ]
```

Boundaries are specified either by date or sequence number. To specify by date:
```
> GET /dir/ref1/:by-date/<starting>/<ending>
...
```

`<starting>` and `<ending>` are both strings that are parseable by a standard date-parsing library (yay ambiguity!).

To specify by sequence number:
```
> GET /dir/ref1/:by-seq/<limit>/<offset>
...
```

If `<limit>` isn't specified it is assumed there is no limit. If `<offset>` isn't specified it is
assumed to be 0. 

## Example
```
> POST /dir/ref1/:abs/5
ok

> GET /dir/ref1/:all
[ { "ts":..., "val":5 } ]

> POST /dir/ref1/:inc
ok

> GET /dir/ref1/:all
[ { "ts":..., "val":5 },
  { "ts":..., "val":6 } ]
  
> POST /dir/ref1/:abs/6
ok

> GET /dir/ref1/:all
[ { "ts":..., "val":5 },
  { "ts":..., "val":6 },
  { "ts":..., "val":6 } ]

> GET /dir/ref1/:by-seq/2
[ { "ts":..., "val":6 },
  { "ts":..., "val":6 } ]

> GET /dir/ref1/:by-seq/2/1
[ { "ts":..., "val":5 },
  { "ts":..., "val":6 } ]
```
