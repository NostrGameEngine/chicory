(module
  (func (export "function-reference") (result funcref i32)
    ref.null func
    i32.const 17)
  (func (export "external-reference") (result externref i32)
    ref.null extern
    i32.const 23)

)
