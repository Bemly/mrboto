# hello.rb - Example Ruby script for mrboto
# Compile with: mruby/build/host/bin/mrbc -o hello.mrb hello.rb

puts "Hello from mruby on Android!"

# Demo: show mruby version
version = MRUBY_VERSION
puts "Running mruby #{version}"

# Demo: simple computation
numbers = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
sum = numbers.reduce(0) { |acc, n| acc + n }
product = numbers.reduce(1) { |acc, n| acc * n }

puts "Sum of 1..10: #{sum}"
puts "Product of 1..10: #{product}"
puts "Factorial of 10: #{product}"

# Demo: string manipulation
greeting = "mrboto"
puts "Greeting reversed: #{greeting.reverse}"
puts "Greeting upcase: #{greeting.upcase}"

# Final result (this is what evalBytecode returns)
"Done! Hello from mruby #{version} on Android."
