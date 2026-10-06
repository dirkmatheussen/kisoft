using System.Collections;
using System.Globalization;
using System.Reflection;
using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Mvc.Controllers;
using Microsoft.AspNetCore.Mvc.Filters;

namespace KnappKiSoftMock.Api.Validation;

/// <summary>
/// Java Bean Validation (jakarta.validation) semantics, mirrored 1:1 from the Spring Boot mock:
/// <list type="bullet">
/// <item><c>@NotBlank</c> → <see cref="NotBlankAttribute"/>, <c>@NotNull</c> → <see cref="NotNullAttribute"/>,
/// <c>@Min</c> → <see cref="MinAttribute"/>, <c>@Pattern</c> → <see cref="PatternAttribute"/>.</item>
/// <item>Nested objects are only validated where Java has <c>@Valid</c> (<see cref="ValidAttribute"/>);
/// <c>List&lt;@Valid X&gt;</c> validates every non-null element.</item>
/// <item>A violation yields HTTP 400 with the Spring default error body (no field details).</item>
/// </list>
/// </summary>
[AttributeUsage(AttributeTargets.Property | AttributeTargets.Parameter)]
public sealed class ValidAttribute : Attribute;

public abstract class ConstraintAttribute : Attribute
{
    public abstract bool IsValid(object? value);
}

/// <summary>jakarta @NotBlank: not null and contains at least one char above U+0020 (Java String.trim semantics).</summary>
[AttributeUsage(AttributeTargets.Property | AttributeTargets.Parameter)]
public sealed class NotBlankAttribute : ConstraintAttribute
{
    public override bool IsValid(object? value) =>
        value is string s && s.Any(c => c > ' ');
}

/// <summary>jakarta @NotNull.</summary>
[AttributeUsage(AttributeTargets.Property | AttributeTargets.Parameter)]
public sealed class NotNullAttribute : ConstraintAttribute
{
    public override bool IsValid(object? value) => value is not null;
}

/// <summary>jakarta @Min: null is valid; numbers must be &gt;= min.</summary>
[AttributeUsage(AttributeTargets.Property | AttributeTargets.Parameter)]
public sealed class MinAttribute(long min) : ConstraintAttribute
{
    public override bool IsValid(object? value) =>
        value is null || Convert.ToDecimal(value, CultureInfo.InvariantCulture) >= min;
}

/// <summary>jakarta @Pattern: null is valid; the whole string must match (Java Matcher.matches()).</summary>
[AttributeUsage(AttributeTargets.Property | AttributeTargets.Parameter)]
public sealed class PatternAttribute(string regexp) : ConstraintAttribute
{
    private readonly Regex _regex = new("^(?:" + regexp + ")$", RegexOptions.CultureInvariant);

    public override bool IsValid(object? value) =>
        value is null || (value is string s && _regex.IsMatch(s));
}

public static class JavaValidator
{
    /// <summary>Validates a root object the way Spring does for <c>@RequestBody @Valid</c>.</summary>
    public static bool IsValid(object? root) => root is null || ValidateObject(root);

    private static bool ValidateObject(object value)
    {
        if (value is string) return true;
        if (value is IEnumerable enumerable)
        {
            // Spring validates List<@Valid X> bodies per element; the DTOs in scope are never raw lists.
            foreach (var item in enumerable)
            {
                if (item is not null && !ValidateObject(item)) return false;
            }
            return true;
        }

        foreach (var property in value.GetType().GetProperties(BindingFlags.Public | BindingFlags.Instance))
        {
            if (property.GetIndexParameters().Length != 0) continue;
            var constraints = property.GetCustomAttributes<ConstraintAttribute>(inherit: true).ToList();
            var cascade = property.GetCustomAttribute<ValidAttribute>(inherit: true) is not null;
            if (constraints.Count == 0 && !cascade) continue;

            var propertyValue = property.GetValue(value);
            foreach (var constraint in constraints)
            {
                if (!constraint.IsValid(propertyValue)) return false;
            }
            if (cascade && propertyValue is not null && !ValidateObject(propertyValue)) return false;
        }
        return true;
    }
}

/// <summary>
/// Runs <see cref="JavaValidator"/> on every action parameter marked <see cref="ValidAttribute"/>
/// (Java <c>@RequestBody @Valid</c>) and answers 400 with the Spring default error body on failure.
/// </summary>
public sealed class ValidateRequestBodyFilter : IActionFilter
{
    public void OnActionExecuting(ActionExecutingContext context)
    {
        foreach (var parameter in context.ActionDescriptor.Parameters.OfType<ControllerParameterDescriptor>())
        {
            if (parameter.ParameterInfo.GetCustomAttribute<ValidAttribute>() is null) continue;
            if (!context.ActionArguments.TryGetValue(parameter.Name, out var argument)) continue;
            if (!JavaValidator.IsValid(argument))
            {
                context.Result = SpringErrors.Result(context.HttpContext, StatusCodes.Status400BadRequest);
                return;
            }
        }
    }

    public void OnActionExecuted(ActionExecutedContext context)
    {
    }
}
