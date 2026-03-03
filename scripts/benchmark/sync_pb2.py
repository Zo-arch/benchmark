# -*- coding: utf-8 -*-
# Código equivalente ao gerado por: protoc -I src/main/proto --python_out=scripts src/main/proto/sync.proto
# Para regenerar (quando tiver protoc): protoc -I src/main/proto --python_out=scripts src/main/proto/sync.proto

from google.protobuf import descriptor_pool
from google.protobuf import message_factory
from google.protobuf.descriptor_pb2 import FileDescriptorSet, FileDescriptorProto, DescriptorProto, FieldDescriptorProto

# Constantes de tipo do protobuf
_TYPE_DOUBLE = 1
_TYPE_INT64 = 3
_TYPE_STRING = 9
_TYPE_MESSAGE = 11
_TYPE_INT32 = 5
_LABEL_REPEATED = 3

# Item: id=1 .. metadata=16 (sync com sync.proto)
item_desc = DescriptorProto(
    name="Item",
    field=[
        FieldDescriptorProto(name="id", number=1, type=_TYPE_INT64, label=1),
        FieldDescriptorProto(name="value_a", number=2, type=_TYPE_DOUBLE, label=1),
        FieldDescriptorProto(name="value_b", number=3, type=_TYPE_DOUBLE, label=1),
        FieldDescriptorProto(name="label", number=4, type=_TYPE_STRING, label=1),
        FieldDescriptorProto(name="latitude", number=5, type=_TYPE_DOUBLE, label=1),
        FieldDescriptorProto(name="longitude", number=6, type=_TYPE_DOUBLE, label=1),
        FieldDescriptorProto(name="altitude", number=7, type=_TYPE_DOUBLE, label=1),
        FieldDescriptorProto(name="created_at", number=8, type=_TYPE_INT64, label=1),
        FieldDescriptorProto(name="updated_at", number=9, type=_TYPE_INT64, label=1),
        FieldDescriptorProto(name="description", number=10, type=_TYPE_STRING, label=1),
        FieldDescriptorProto(name="code", number=11, type=_TYPE_STRING, label=1),
        FieldDescriptorProto(name="category", number=12, type=_TYPE_STRING, label=1),
        FieldDescriptorProto(name="status", number=13, type=_TYPE_STRING, label=1),
        FieldDescriptorProto(name="count", number=14, type=_TYPE_INT32, label=1),
        FieldDescriptorProto(name="score", number=15, type=_TYPE_DOUBLE, label=1),
        FieldDescriptorProto(name="metadata", number=16, type=_TYPE_STRING, label=1),
    ],
)

# SnapshotResponse: snapshot_timestamp=1, generated_at=2, items=3 (repeated Item)
# Referência ao Item usa number do tipo no FileDescriptorProto (index 1 = Item)
snapshot_desc = DescriptorProto(
    name="SnapshotResponse",
    field=[
        FieldDescriptorProto(name="snapshot_timestamp", number=1, type=_TYPE_INT64, label=1),
        FieldDescriptorProto(name="generated_at", number=2, type=_TYPE_STRING, label=1),
        FieldDescriptorProto(name="items", number=3, type=_TYPE_MESSAGE, label=_LABEL_REPEATED, type_name=".benchmark.sync.Item"),
    ],
)

file_desc = FileDescriptorProto(
    name="sync.proto",
    package="benchmark.sync",
    message_type=[item_desc, snapshot_desc],
)

pool = descriptor_pool.DescriptorPool()
pool.Add(file_desc)

_factory = message_factory.MessageFactory(pool)
Item = _factory.GetPrototype(pool.FindMessageTypeByName("benchmark.sync.Item"))
SnapshotResponse = _factory.GetPrototype(pool.FindMessageTypeByName("benchmark.sync.SnapshotResponse"))

__all__ = ["Item", "SnapshotResponse"]
