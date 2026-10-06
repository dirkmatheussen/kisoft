namespace KnappKiSoftMock.Persistence;

/// <summary>
/// Java <c>@Transactional</c> (propagation REQUIRED): all SaveChanges inside <paramref name="work"/>
/// commit together or roll back together when an exception escapes; joins an outer transaction if present.
/// </summary>
public static class DbTransactions
{
    public static T InTransaction<T>(this AppDbContext db, Func<T> work)
    {
        if (db.Database.CurrentTransaction is not null)
        {
            return work();
        }
        using var transaction = db.Database.BeginTransaction();
        var result = work();
        transaction.Commit();
        return result;
    }

    public static void InTransaction(this AppDbContext db, Action work) =>
        db.InTransaction(() =>
        {
            work();
            return true;
        });
}
