package com.servpp.multiexpr;

/**
 * @author lidong@date 2024-07-31@version 1.0
 */
public class MathAndLogicUtil {
    /**
     * Adds two Number objects.
     *
     * @param num1 The first Number object
     * @param num2 The second Number object
     * @return The sum of the two Number objects
     */
    public static Number add(Number num1, Number num2) {
        // Extract the double values from the Number objects
        double value1 = num1.doubleValue();
        double value2 = num2.doubleValue();

        // Calculate the sum
        double sum = value1 + value2;

        // Return the appropriate Number subclass based on input types
        if (num1 instanceof Double || num2 instanceof Double) {
            return sum; // If either input is Double, return Double
        } else if (num1 instanceof Float || num2 instanceof Float) {
            return (float) sum; // If either input is Float, return Float
        } else {
            return (int) sum; // Default to Integer
        }
    }
    /**
     * Multiplies two Number objects.
     *
     * @param num1 The first Number object
     * @param num2 The second Number object
     * @return The product of the two Number objects
     */
    public static Number multiply(Number num1, Number num2) {
        // Extract the double values from the Number objects
        double value1 = num1.doubleValue();
        double value2 = num2.doubleValue();

        // Calculate the product
        double product = value1 * value2;

        // Return the appropriate Number subclass based on input types
        if (num1 instanceof Double || num2 instanceof Double) {
            return product; // If either input is Double, return Double
        } else if (num1 instanceof Float || num2 instanceof Float) {
            return (float) product; // If either input is Float, return Float
        } else {
            return (int) product; // Default to Integer
        }
    }
    /**
     * Divides one Number object by another.
     *
     * @param num1 The dividend Number object
     * @param num2 The divisor Number object
     * @return The result of the division
     * @throws ArithmeticException if the divisor is zero
     */
    public static Number divide(Number num1, Number num2) {
        // Extract the double values from the Number objects
        double value1 = num1.doubleValue();
        double value2 = num2.doubleValue();

        // Check for division by zero
        if (value2 == 0) {
            throw new ArithmeticException("Division by zero");
        }

        // Calculate the result
        double result = value1 / value2;

        // Return the appropriate Number subclass based on input types
        if (num1 instanceof Double || num2 instanceof Double) {
            return result; // If either input is Double, return Double
        } else if (num1 instanceof Float || num2 instanceof Float) {
            return (float) result; // If either input is Float, return Float
        } else {
            return (int) result; // Default to Integer
        }
    }
    /**
     * Subtracts one Number object from another.
     *
     * @param num1 The minuend Number object
     * @param num2 The subtrahend Number object
     * @return The result of the subtraction
     */
    public static Number subtract(Number num1, Number num2) {
        // Extract the double values from the Number objects
        double value1 = num1.doubleValue();
        double value2 = num2.doubleValue();

        // Calculate the result
        double result = value1 - value2;

        // Return the appropriate Number subclass based on input types
        if (num1 instanceof Double || num2 instanceof Double) {
            return result; // If either input is Double, return Double
        } else if (num1 instanceof Float || num2 instanceof Float) {
            return (float) result; // If either input is Float, return Float
        } else {
            return (int) result; // Default to Integer
        }
    }

    public static boolean compareNumberAndString(String op, Object left, Object right){
        boolean ret = false;
        double val = 0;
        if(left instanceof Number && right instanceof Number){
            val = MathAndLogicUtil.subtract((Number) left, (Number) right).doubleValue();
        }else{
            String l = ""+left;
            String r = ""+right;
            val = l.compareTo(r);
        }
        switch (op) {
            case "==":
                ret = val == 0;
                break;
            case "!=":
                ret = val != 0;
                break;
            case ">":
                ret = val > 0;
                break;
            case "<":
                ret = val < 0;
                break;
            case ">=":
                ret = val >= 0;
                break;
            case "<=":
                ret = val <= 0;
                break;
            default:
                throw new IllegalArgumentException("Unknown operator: " + op);
        }
        return ret;
    }

}
