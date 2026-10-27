(module
  (type $t (func (param i32) (result i32)))
  (table 1 funcref)
  (func (type $t) (param i32) (result i32)
    local.get 0
    i32.const 1
    i32.add)
  (func (export "run") (param i32) (result i32)
    local.get 0
    i32.const 0
    return_call_indirect (type $t)))
