(module
  (tag $e)

  (func (export "nested-handlers") (result i32)
    (block $outer
      (try_table (catch $e $outer)
        (block $inner
          (try_table (catch $e $inner)
            (throw $e))
          (return (i32.const 1)))
        (return (i32.const 42))))
    i32.const 99)

  (func (export "unreachable-handler") (result i32)
    (return (i32.const 7))
    (block $unused
      (try_table (catch_all $unused)
        unreachable))
    i32.const 9)
)
