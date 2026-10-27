(module
 (memory (export "memory") 1)
 (table 1 funcref)
 (elem (i32.const 0) $self)
 (func $self (export "self") (result funcref) ref.func $self)
 (func (export "run") (param i32) (result i32)
  (local i32)
  (loop $loop
   (block local.get 1 i32.const 1 i32.add local.set 1)
   local.get 1 local.get 0 i32.lt_u br_if $loop)
  i32.const 0 local.get 1 i32.store
  i32.const 0 i32.load)
 (func $tail (export "tail") (param i32) (result i32)
  local.get 0 i32.eqz
  if (result i32) i32.const 37
  else local.get 0 i32.const 1 i32.sub return_call $tail end)
 (func (export "trap") (result i32) i32.const -1 i32.load)
 (func (export "table") (param i32) (result i32)
  (local i32)
  (block $done
   (loop $again
    local.get 1 i32.const 1 i32.add local.set 1
    local.get 1 local.get 0 i32.lt_u i32.eqz
    br_table $again $done))
  local.get 1))
