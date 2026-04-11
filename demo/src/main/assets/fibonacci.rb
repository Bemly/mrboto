# 斐波那契
def fib(n)
  n <= 1 ? n : fib(n - 1) + fib(n - 2)
end
fib(15)
